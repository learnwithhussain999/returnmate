# Deploying ReturnMate on an Oracle Cloud Always Free VM

One VM runs everything with Docker Compose:

| Container | What it does | Reachable from the internet? |
|---|---|---|
| `web` (Caddy) | HTTPS certificate, serves the React UI, forwards `/api/*` to Spring | Yes, ports 80 and 443 |
| `api` (Spring Boot) | The REST API | No, only through Caddy |
| `db` (Postgres 17) | Data, stored in the `db_data` volume | No, on an internal network |

The URL is `https://<ip-with-dashes>.sslip.io`. [sslip.io](https://sslip.io) turns any IP into a DNS name, so Let's Encrypt can issue a real certificate without you buying a domain.

## 1. Create the VM (Oracle Cloud console)
1. Sign up at cloud.oracle.com. A card is needed for identity checks; Always Free resources aren't charged. **The home region can't be changed later**, so pick one near you.
2. **Compute → Instances → Create instance**
   - **Image:** Canonical Ubuntu 24.04
   - **Shape:** Ampere `VM.Standard.A1.Flex`, **2 OCPU, 12 GB** (the free allowance is 4 OCPU / 24 GB in total)
     - If you see "Out of capacity", try another availability domain, or try again later
   - **Networking:** create a new VCN with a public subnet, and tick **Assign a public IPv4 address**
   - **SSH keys:** upload your public key, `~/.ssh/id_ed25519.pub`
     - Don't have one? Create it with `ssh-keygen -t ed25519`
3. Note the instance's **public IP**.

## 2. Open ports 80 and 443 (two firewalls)
1. **Oracle's firewall** (the security list): Instance → subnet → Security Lists → Default → **Add Ingress Rules**
   - Source `0.0.0.0/0`, TCP, destination port `80`
   - The same again for port `443`
2. **The VM's own firewall.** Oracle's Ubuntu image blocks everything except SSH. After you SSH in:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80  -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

## 3. Install Docker and start ReturnMate
```bash
ssh ubuntu@<public-ip>

curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit                                   # log out and back in so the docker group applies
ssh ubuntu@<public-ip>

git clone https://github.com/learnwithhussain999/returnmate.git
cd returnmate/deploy
./init-env.sh <public-ip>              # creates .env with random secrets, and prints your URL
docker compose up -d --build           # the first build takes about 5 minutes
docker compose logs -f api             # wait for "Started ReturnmateCoreApplication", then Ctrl+C
```
Open the printed `https://….sslip.io` URL. Caddy gets the certificate on the first visit, which takes a few seconds.

**Back up `deploy/.env`** somewhere private, such as a password manager. If you lose `TOKEN_KEK_BASE64`, encrypted data can't be read.

## Everyday commands (run in `~/returnmate/deploy`)
| Task | Command |
|---|---|
| Deploy the latest `main` | `git pull && docker compose up -d --build` |
| Status | `docker compose ps` |
| Logs | `docker compose logs -f api` (or `web`, `db`) |
| Back up the database | `docker compose exec -T db pg_dump -U returnmate returnmate \| gzip > backup-$(date +%F).sql.gz` |
| Stop everything | `docker compose down` (data is kept; `down -v` **deletes** it) |

## Test the same setup locally
```bash
cd deploy
cat > /tmp/rm-test.env <<ENV
SITE_ADDRESS=:80
HTTP_PORT=8088
HTTPS_PORT=8443
DB_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET_BASE64=$(openssl rand -base64 48)
TOKEN_KEK_BASE64=$(openssl rand -base64 32)
ENV
docker compose -p rm-test --env-file /tmp/rm-test.env up -d --build    # http://localhost:8088
docker compose -p rm-test down -v                                      # clean up
```
