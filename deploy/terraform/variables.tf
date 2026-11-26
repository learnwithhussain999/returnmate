# Inputs. Values go in terraform.tfvars (git-ignored); see terraform.tfvars.example.

variable "tenancy_ocid" {
  description = "Your tenancy OCID (also the root compartment). Found in ~/.oci/config."
  type        = string
}

variable "region" {
  description = "Your home region, for example eu-frankfurt-1."
  type        = string
}

variable "ssh_public_key_path" {
  description = "Public key copied onto the VM so you can SSH in."
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "ssh_allowed_cidr" {
  description = "Who may reach port 22. Use \"<your-ip>/32\" to allow only your own network."
  type        = string
  default     = "0.0.0.0/0"
}

variable "availability_domain_index" {
  description = "Which availability domain to use (0, 1 or 2). Change it if you get \"Out of capacity\"."
  type        = number
  default     = 0
}

# Always Free allows 4 OCPU / 24 GB of Ampere A1 in total.
variable "ocpus" {
  type    = number
  default = 2
}

variable "memory_in_gbs" {
  type    = number
  default = 12
}
