# Which Terraform and which provider plugin this code needs.
# A "provider" is the plugin that knows how to talk to one cloud's API.
terraform {
  required_version = ">= 1.6"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = ">= 6.0"
    }
  }
}

# With no keys written here, the provider reads ~/.oci/config (the DEFAULT profile).
# That keeps your API key out of this repo.
provider "oci" {
  region = var.region
}
