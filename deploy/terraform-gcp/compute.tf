# Free tier rules this VM must follow to cost $0:
#   - machine type e2-micro, in us-west1 / us-central1 / us-east1
#   - one VM only (750 free hours a month = one VM running all month)
#   - boot disk "pd-standard", 30 GB at most (pd-balanced or pd-ssd are charged)

resource "google_compute_instance" "app" {
  name         = "returnmate"
  machine_type = "e2-micro"
  zone         = var.zone
  tags         = ["returnmate"]

  # `terraform destroy` refuses while this is true. The database lives on this disk.
  deletion_protection = true

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2404-lts-amd64"
      type  = "pd-standard"
      size  = 30
    }
  }

  network_interface {
    subnetwork = google_compute_subnetwork.main.id
    # An empty access_config block gives the VM a public IP.
    access_config {}
  }

  metadata = {
    ssh-keys = "${var.ssh_user}:${trimspace(file(pathexpand(var.ssh_public_key_path)))}"
    # Ubuntu's cloud-init reads this on the first boot.
    user-data = file("${path.module}/cloud-init.yaml")
  }

  lifecycle {
    # Same reason as the Oracle VM: don't rebuild the VM (and lose the data)
    # when Ubuntu publishes a new image or cloud-init.yaml is edited.
    ignore_changes = [
      boot_disk[0].initialize_params[0].image,
      metadata["user-data"],
    ]
  }
}
