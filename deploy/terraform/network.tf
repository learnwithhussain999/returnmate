# The network the console wizard made for you by hand, written out piece by piece:
# VCN (private network) -> internet gateway (door to the internet)
# -> route table (send outside traffic through that door)
# -> security list (Oracle's firewall) -> public subnet (where the VM lives).

resource "oci_core_vcn" "main" {
  compartment_id = var.tenancy_ocid
  display_name   = "returnmate-vcn"
  cidr_blocks    = ["10.0.0.0/16"]
  dns_label      = "returnmate"
}

resource "oci_core_internet_gateway" "main" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "returnmate-igw"
  enabled        = true
}

resource "oci_core_route_table" "public" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "returnmate-public-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.main.id
  }
}

# Replaces README step 2.1 ("Add Ingress Rules" in the console).
resource "oci_core_security_list" "public" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.main.id
  display_name   = "returnmate-public-sl"

  # The VM may call out anywhere (apt, Docker Hub, Let's Encrypt).
  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  # Protocol numbers: 6 = TCP, 1 = ICMP.
  ingress_security_rules {
    description = "SSH"
    source      = var.ssh_allowed_cidr
    protocol    = "6"
    tcp_options {
      min = 22
      max = 22
    }
  }

  ingress_security_rules {
    description = "HTTP (Caddy redirects it to HTTPS, and Let's Encrypt uses it)"
    source      = "0.0.0.0/0"
    protocol    = "6"
    tcp_options {
      min = 80
      max = 80
    }
  }

  ingress_security_rules {
    description = "HTTPS"
    source      = "0.0.0.0/0"
    protocol    = "6"
    tcp_options {
      min = 443
      max = 443
    }
  }

  # "Fragmentation needed" messages. Without them some large packets silently vanish.
  ingress_security_rules {
    description = "ICMP path MTU discovery"
    source      = "0.0.0.0/0"
    protocol    = "1"
    icmp_options {
      type = 3
      code = 4
    }
  }
}

resource "oci_core_subnet" "public" {
  compartment_id    = var.tenancy_ocid
  vcn_id            = oci_core_vcn.main.id
  display_name      = "returnmate-public-subnet"
  cidr_block        = "10.0.1.0/24"
  dns_label         = "public"
  route_table_id    = oci_core_route_table.public.id
  security_list_ids = [oci_core_security_list.public.id]
}
