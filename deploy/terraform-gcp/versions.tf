terraform {
  required_version = ">= 1.6"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 6.0"
    }
  }
}

# Logs in with "Application Default Credentials": run
#   gcloud auth application-default login
# once, and no key file is needed in this repo.
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# The Compute Engine API is off in a new project; this turns it on.
resource "google_project_service" "compute" {
  service            = "compute.googleapis.com"
  disable_on_destroy = false
}
