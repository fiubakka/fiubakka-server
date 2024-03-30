import dagger
import anyio
import os

def kubectl(container: dagger.Container) -> dagger.Container:
    return container.with_exec(shell("apt-get update && apt-get install -y curl")) \
            .with_exec(["curl", "-LO", "https://dl.k8s.io/release/v1.29.2/bin/linux/arm64/kubectl"]) \
            .with_exec(["install", "-o", "root", "-g", "root", "-m", "0755", "kubectl", "/usr/local/bin/kubectl"])

def cloudflared(container: dagger.Container) -> dagger.Container:
    # We are using the cfdtunnel wrapper on top of cloudflared to avoid having to run
    # cloudflared as a systemd service, which is not trivial to do in Docker containers.
    # See https://github.com/mmiranda/cfdtunnel
    return container.with_exec(["mkdir", "-p", "--mode=0755", "/usr/share/keyrings"]) \
        .with_exec(shell("curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg | tee /usr/share/keyrings/cloudflare-main.gpg >/dev/null")) \
        .with_exec(shell("echo 'deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared bookworm main' | tee /etc/apt/sources.list.d/cloudflared.list")) \
        .with_exec(shell("apt-get update && apt-get install -y cloudflared")) \
        .with_exec(shell("curl -LO https://github.com/mmiranda/cfdtunnel/releases/download/v0.1.4/cfdtunnel_0.1.4_Linux_arm64.tar.gz")) \
        .with_exec(shell("tar -xvzf cfdtunnel_0.1.4_Linux_arm64.tar.gz")) \
        .with_exec(shell("mkdir /root/.cfdtunnel")) \
        .with_exec(shell("mkdir /root/.kube")) \
        .with_exec(shell("echo '\
            [k8s] \
            \nhost = https://kubernetes.marcosrolando.uk \
            \nport = 6443 \
            \nenv = HTTPS_PROXY=socks5://127.0.0.1:6443' > /root/.cfdtunnel/config"
        )) \
        .with_exec(shell(f"echo 'apiVersion: v1 \
            \nclusters: \
            \n- cluster: \
                \n    certificate-authority-data: {os.environ["K8S_SERVER_CA"]} \
                \n    server: https://raspberrypi1:6443 \
            \n  name: k3s-local-cluster \
            \ncontexts: \
            \n- context: \
                \n    cluster: k3s-local-cluster \
                \n    user: k3s-local-cluster \
            \n  name: k3s-local-cluster \
            \ncurrent-context: k3s-local-cluster \
            \nkind: Config \
            \npreferences: {{}} \
            \nusers: \
            \n- name: k3s-local-cluster \
                \n  user: \
                \n    client-certificate-data: {os.environ["K8S_CLIENT_CA"]} \
                \n    client-key-data: {os.environ["K8S_CLIENT_KEY"]}' > /root/.kube/config"))

def shell(cmd: str) -> list[str]:
    return ["sh", "-c", cmd]


async def main():
    async with dagger.Connection() as client:
        await client.container().from_("docker.io/debian:12.5-slim") \
            .with_(kubectl) \
            .with_(cloudflared) \
            .with_entrypoint(["./cfdtunnel", "--profile", "k8s", "--"]) \
            .with_default_args(args=["env", "HTTPS_PROXY=socks5://127.0.0.1:6443", "kubectl", "get", "po", "-n", "fiubakka-server-1"]) \
            .with_exec() \
            .stdout()

anyio.run(main)
