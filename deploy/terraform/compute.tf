# "data" blocks only look things up; they never create anything.

data "oci_identity_availability_domains" "all" {
  compartment_id = var.tenancy_ocid
}

# The newest Ubuntu 24.04 image built for Ampere (ARM) shapes.
data "oci_core_images" "ubuntu" {
  compartment_id           = var.tenancy_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

resource "oci_core_instance" "app" {
  compartment_id      = var.tenancy_ocid
  availability_domain = data.oci_identity_availability_domains.all.availability_domains[var.availability_domain_index].name
  display_name        = "returnmate"
  shape               = "VM.Standard.A1.Flex"

  shape_config {
    ocpus         = var.ocpus
    memory_in_gbs = var.memory_in_gbs
  }

  source_details {
    source_type = "image"
    source_id   = data.oci_core_images.ubuntu.images[0].id
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    assign_public_ip = true
  }

  metadata = {
    ssh_authorized_keys = file(pathexpand(var.ssh_public_key_path))
    # cloud-init runs this once, on the first boot (README steps 2.2 and the Docker install).
    user_data = base64encode(file("${path.module}/cloud-init.yaml"))
  }

  lifecycle {
    # The database lives on this VM's disk. Without this, a newer Ubuntu image
    # (or an edit to cloud-init.yaml) would make `terraform apply` destroy and
    # recreate the VM, and the data with it.
    ignore_changes = [
      source_details[0].source_id,
      metadata["user_data"],
    ]
  }
}
