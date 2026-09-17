# One-domain ngrok gateway

This Docker-only setup publishes both local applications through the account's single assigned
ngrok development domain. Nothing needs to be installed on the host except Docker and curl.

- `/bulk-sms/` routes to Bulk Message Composer on host port `8080`.
- All other paths route to Message Approval System on host port `8081`.

Nginx listens locally on port `8090`; ngrok publishes that gateway over HTTPS. Both containers use
host networking because the application containers publish ports `8080` and `8081` on the Linux host.

## Configure

Rotate any token that has been shared publicly. Then create the private environment file:

```bash
cd ngrok
cp .env.example .env
nano .env
```

Set the new authtoken and the assigned dev domain without `https://` or a trailing slash.
Never commit `.env`.

## Start

Start Bulk Message Composer and Message Approval System first, then run:

```bash
chmod +x start.sh
./start.sh
```

The public addresses are:

- `https://YOUR_DOMAIN/bulk-sms/` — Bulk Message Composer
- `https://YOUR_DOMAIN/` — Message Approval System

The same assigned domain is reused after restarts. The local ngrok inspector is available at
`http://127.0.0.1:4040` while ngrok is running.



docker compose down --rmi all --volumes
