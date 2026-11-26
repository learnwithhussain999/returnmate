#!/usr/bin/env bash
# Free Ampere A1 capacity is often full. This retries `terraform apply`,
# rotating through the availability domains, until the VM is created.
#
# Safety: each attempt first saves a plan and checks that its ONLY change is
# creating oci_core_instance.app. Anything else (a change, a destroy) stops the loop.
#
# Usage: ./retry-apply.sh [seconds-between-rounds]    (default 60; Ctrl+C to stop)
set -uo pipefail
cd "$(dirname "$0")"

WAIT="${1:-60}"
ADS=(0 1 2)
round=1

while true; do
  for ad in "${ADS[@]}"; do
    echo "[$(date +%H:%M:%S)] round $round, AD index $ad"

    terraform plan -input=false -no-color -var "availability_domain_index=$ad" -out=tfplan >/dev/null || {
      echo "plan failed; stopping"; exit 1; }

    # List every planned change as "address:action". If this check itself fails,
    # stop: an empty answer must never be mistaken for "nothing to do".
    changes=$(terraform show -json tfplan | python3 -c '
import json, sys
plan = json.load(sys.stdin)
print(" ".join(c["address"] + ":" + "+".join(c["change"]["actions"])
               for c in plan.get("resource_changes", [])
               if c["change"]["actions"] != ["no-op"]))') || {
      echo "Could not read the plan; stopping"; exit 1; }

    if [[ -z "$changes" ]]; then
      echo "Nothing to do: the VM already exists."; terraform output; exit 0
    fi
    if [[ "$changes" != "oci_core_instance.app:create" ]]; then
      echo "Unexpected plan, stopping so you can look: $changes"; exit 1
    fi

    if out=$(terraform apply -input=false -no-color tfplan 2>&1); then
      echo "Created!"; terraform output; exit 0
    fi
    if grep -q "Out of host capacity" <<<"$out"; then
      echo "  out of capacity"
    else
      echo "$out"; echo "Failed with something other than capacity; stopping."; exit 1
    fi
  done
  round=$((round + 1))
  sleep "$WAIT"
done
