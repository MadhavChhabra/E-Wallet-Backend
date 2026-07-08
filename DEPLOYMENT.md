# Deployment runbook — AWS EC2 Free Tier (Dockerized)

A fully free stack: **Spring Boot + MySQL in Docker on an EC2 `t3.micro`**, behind
**Caddy** (automatic free HTTPS via Let's Encrypt) on a free **DuckDNS** domain.
CI/CD is GitHub Actions; the image lives in GHCR (free).

```
Browser ──HTTPS──> Caddy (:443) ──> app (:8080) ──> MySQL
                    (Let's Encrypt)     (Spring Boot)   (Docker volume)
```

> **Cost warning:** Free Tier lasts **12 months**, then this instance is billed
> (~a few $/mo). **Do Step 7 (billing alarm) first**, and stop/terminate the
> instance when you're done demoing.

---

## 0. Prerequisites
- An AWS account, a GitHub account (repo already at `MadhavChhabra/E-Wallet-Backend`).
- Razorpay **test** keys (optional but recommended for the top-up demo).

## 1. Launch the EC2 instance
1. EC2 → Launch instance. **Ubuntu 24.04 LTS**, type **`t3.micro`** (Free Tier eligible).
2. Create/download a key pair (`.pem`) — you'll need it for SSH + the deploy secret.
3. Storage: 20 GB gp3 (Free Tier allows up to 30 GB).
4. **Security group** — allow inbound: `22` (SSH, your IP only), `80` (HTTP), `443` (HTTPS).
5. Launch. Allocate an **Elastic IP** and associate it (keeps the IP stable across restarts).

## 2. Free domain (DuckDNS)
1. Go to <https://www.duckdns.org>, sign in, create a subdomain e.g. `ewallet-madhav`.
2. Set its IP to your Elastic IP. You now have `ewallet-madhav.duckdns.org`.

## 3. Install Docker on the instance
SSH in (`ssh -i key.pem ubuntu@<elastic-ip>`) and run:
```bash
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER && newgrp docker
# 1 GB RAM is tight — add 2 GB swap so nothing gets OOM-killed:
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 4. Put the deploy files on the box
From the repo's `deploy/` folder, copy `docker-compose.yml`, `Caddyfile`, and a
filled-in `.env` into `~/ewallet` on the instance:
```bash
ssh -i key.pem ubuntu@<ip> 'mkdir -p ~/ewallet'
scp -i key.pem deploy/docker-compose.yml deploy/Caddyfile ubuntu@<ip>:~/ewallet/
# create ~/ewallet/.env from deploy/.env.example and fill in real values (DOMAIN,
# DB_PASSWORD, JWT_SECRET, REFRESH_SECRET, CORS_ORIGINS, Razorpay keys, ...)
```

## 5. Make the image pullable + add GitHub secrets
1. Repo → **Settings → Secrets and variables → Actions** → add:
   - `EC2_HOST` = your Elastic IP
   - `EC2_USER` = `ubuntu`
   - `EC2_SSH_KEY` = the **contents** of your `.pem` private key
2. After the first deploy, GitHub → your profile → **Packages → e-wallet-backend →
   Package settings → Change visibility → Public** (so EC2 can `docker pull`
   without logging in). Alternatively `docker login ghcr.io` on the box with a PAT.

## 6. Deploy
1. Push this branch to GitHub (CI runs automatically: build + tests).
2. Actions → **Deploy Backend (EC2)** → **Run workflow**. It builds the image,
   pushes to GHCR, SSHes in, and `docker compose up -d`.
3. First boot: Caddy fetches the TLS cert (needs ports 80/443 open + DNS pointing
   at the box). Check:
   ```bash
   ssh -i key.pem ubuntu@<ip> 'cd ~/ewallet && docker compose ps && docker compose logs -n 50 app'
   curl https://ewallet-madhav.duckdns.org/actuator/health   # -> {"status":"UP"}
   ```

## 7. Cost safety (do this once, early)
AWS → **Billing → Budgets → Create budget** → *Zero-spend* or a **$1 cost budget**
with an email alert. Also set a **CloudWatch billing alarm**. This is your seatbelt.

## 8. Point the apps at the backend
- **Flutter (mobile/web):** build with `--dart-define=API_BASE_URL=https://ewallet-madhav.duckdns.org`.
- **CORS:** set `CORS_ORIGINS` in `.env` to your web demo origin
  (e.g. `https://madhavchhabra.github.io`) and `docker compose up -d` again.
- **Razorpay webhook:** dashboard → Webhooks → URL
  `https://ewallet-madhav.duckdns.org/api/v1/payments/razorpay/webhook`,
  event `payment.captured`, secret = `RAZORPAY_WEBHOOK_SECRET`.

## 9. Updating
Push code → run the **Deploy** workflow again (pulls the new image, zero-config).

## 10. Teardown (stop paying)
`docker compose down` on the box, then **EC2 → Instance → Terminate**, release the
Elastic IP, and delete the DuckDNS domain.
