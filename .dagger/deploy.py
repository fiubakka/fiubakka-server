import dagger
import anyio

def kubectl(container: dagger.Container) -> dagger.Container:
    return container.with_exec(shell("apt-get update && apt-get install -y curl")) \
            .with_exec(["curl", "-LO", "https://dl.k8s.io/release/v1.29.2/bin/linux/amd64/kubectl"]) \
            .with_exec(["install", "-o", "root", "-g", "root", "-m", "0755", "kubectl", "/usr/local/bin/kubectl"])

def cloudflared(container: dagger.Container) -> dagger.Container:
    return container.with_exec(["mkdir", "-p", "--mode=0755", "/usr/share/keyrings"]) \
        .with_exec(shell("curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg | tee /usr/share/keyrings/cloudflare-main.gpg >/dev/null")) \
        .with_exec(shell("echo 'deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared bookworm main' | tee /etc/apt/sources.list.d/cloudflared.list")) \
        .with_exec(shell("apt-get update && apt-get install -y cloudflared systemctl")) \
        .with_exec(shell("echo '\
            \n[Unit] \
                \n\tDescription=Cloudflared for Kubernetes \
                \n\tAfter=network.target \
            \n[Service] \
                \n\tType=simple \
                \n\tExecStart=/usr/local/bin/cloudflared access tcp --hostname kubernetes.marcosrolando.uk --url 127.0.0.1:6443 \
                \n\tRestart=always \
                \n\tUser=root \
            \n[Install] \
                \n\tWantedBy=multi-user.target \
            ' >/etc/systemd/system/k3spi.service")) \
        .with_exec(shell("systemctl enable k3spi.service")) \
        .with_exec(shell("systemctl start k3spi.service")) \

def shell(cmd: str, background = False) -> list[str]:
    return ["sh", "-c", cmd, "&"] if background else ["sh", "-c", cmd]


async def main():
    async with dagger.Connection() as client:
        await client.container().from_("docker.io/debian:12.5-slim") \
            .with_(kubectl) \
            .with_(cloudflared) \
            .with_exec(shell("ps -e")) \
            .stdout()

anyio.run(main)
