# Values go in terraform.tfvars (git-ignored); see terraform.tfvars.example.

variable "project_id" {
  description = "Your Google Cloud project ID (not the name), for example returnmate-123456."
  type        = string
}

# The free e2-micro only exists in us-west1 (Oregon), us-central1 (Iowa) and us-east1 (South Carolina).
variable "region" {
  type    = string
  default = "us-west1"

  validation {
    condition     = contains(["us-west1", "us-central1", "us-east1"], var.region)
    error_message = "The free e2-micro is only free in us-west1, us-central1 or us-east1."
  }
}

variable "zone" {
  type    = string
  default = "us-west1-b"
}

variable "ssh_user" {
  description = "Linux user created on the VM for your SSH key."
  type        = string
  default     = "ubuntu"
}

variable "ssh_public_key_path" {
  type    = string
  default = "~/.ssh/id_ed25519.pub"
}

variable "ssh_allowed_cidr" {
  description = "Who may reach port 22. Use \"<your-ip>/32\" to allow only your own network."
  type        = string
  default     = "0.0.0.0/0"
}
