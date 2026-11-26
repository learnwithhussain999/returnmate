# Same idea as the Oracle VCN, with fewer pieces: Google VPCs already have a route to the
# internet, so there's no gateway or route table to declare.

resource "google_compute_network" "main" {
  name                    = "returnmate-vpc"
  auto_create_subnetworks = false
  depends_on              = [google_project_service.compute]
}

resource "google_compute_subnetwork" "main" {
  name          = "returnmate-subnet"
  network       = google_compute_network.main.id
  region        = var.region
  ip_cidr_range = "10.0.1.0/24"
}

# Google firewall rules apply to VMs carrying a matching "target tag".
resource "google_compute_firewall" "web" {
  name          = "returnmate-allow-web"
  network       = google_compute_network.main.id
  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["returnmate"]

  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }
}

resource "google_compute_firewall" "ssh" {
  name          = "returnmate-allow-ssh"
  network       = google_compute_network.main.id
  source_ranges = [var.ssh_allowed_cidr]
  target_tags   = ["returnmate"]

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}
