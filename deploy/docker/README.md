# Docker demo package

This directory runs the demo frontend, seven Java services, database migration,
MySQL, Redis, RabbitMQ, Nacos, Mailpit, and persistent upload storage as one
Docker Compose application.

## Local build and run (PowerShell 7)

From `D:\works\semi-overt-backend`:

```powershell
pwsh -NoProfile -File .\scripts\docker-demo.ps1 init
pwsh -NoProfile -File .\scripts\docker-demo.ps1 build
pwsh -NoProfile -File .\scripts\docker-demo.ps1 up
pwsh -NoProfile -File .\scripts\docker-demo.ps1 status
```

Open:

- Application: `http://localhost:18000`
- Gateway diagnostics: `http://127.0.0.1:18080`
- Mailpit: `http://127.0.0.1:18025`

The build command expects the frontend checkout at the sibling path
`D:\works\semi-overt-frontend`. Override it with `-FrontendPath` when needed.

## Pull and run from a registry

After both images have been published and made readable in the registry, a
target computer only needs this directory and Docker Compose:

```powershell
pwsh -NoProfile -File .\scripts\docker-demo.ps1 init
pwsh -NoProfile -File .\scripts\docker-demo.ps1 pull
pwsh -NoProfile -File .\scripts\docker-demo.ps1 up
```

The offline archive excludes the optional Adminer image to reduce transfer size. The
core frontend/backend stack is complete; the 	ools profile can pull Adminer later
when the target computer has network access.

The default image names are:

- `ghcr.io/p1nl/semi-overt-backend:demo`
- `ghcr.io/p1nl/semi-overt-frontend:demo`

GitHub Actions workflows in the two repositories can publish those images.
GitHub Container Registry packages may need to be changed to **Public** once so
another computer can pull them without `docker login`.

## Offline transfer

On the build computer:

```powershell
pwsh -NoProfile -File .\scripts\docker-demo.ps1 export -OutputPath D:\semi-overt-docker-demo
```

Copy the output directory to the target computer, open PowerShell 7 in that
output directory, then run:

```powershell
pwsh -NoProfile -File .\docker-demo.ps1 import
pwsh -NoProfile -File .\docker-demo.ps1 init
pwsh -NoProfile -File .\docker-demo.ps1 up
```

## Operations

```powershell
pwsh -NoProfile -File .\scripts\docker-demo.ps1 logs
pwsh -NoProfile -File .\scripts\docker-demo.ps1 down
```

`down` preserves MySQL, Redis, RabbitMQ, Nacos, Mailpit, and upload volumes. The
script intentionally has no automatic volume-deletion command.
