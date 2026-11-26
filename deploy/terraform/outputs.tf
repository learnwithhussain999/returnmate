# Printed after `terraform apply`; read again any time with `terraform output`.

output "public_ip" {
  value = oci_core_instance.app.public_ip
}

output "ssh_command" {
  value = "ssh ubuntu@${oci_core_instance.app.public_ip}"
}

output "app_url" {
  value = "https://${replace(oci_core_instance.app.public_ip, ".", "-")}.sslip.io"
}
