locals {
  public_ip = google_compute_instance.app.network_interface[0].access_config[0].nat_ip
}

output "public_ip" {
  value = local.public_ip
}

output "ssh_command" {
  value = "ssh ${var.ssh_user}@${local.public_ip}"
}

output "app_url" {
  value = "https://${replace(local.public_ip, ".", "-")}.sslip.io"
}
