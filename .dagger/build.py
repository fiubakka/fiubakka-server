import dagger
import anyio
import subprocess
import os

async def main():
    subprocess.run(["git", "submodule", "update", "--init"])

    for env_var in ["DOCKER_USER", "DOCKER_PASS", "DOCKER_REPO"]:
        if env_var not in os.environ:
            raise OSError(f"{env_var} environment variable must be set")
    commit_sha = subprocess.check_output(["git", "rev-parse", "HEAD"]).strip().decode("utf-8")[:7]

    async with dagger.Connection() as client, anyio.create_task_group() as tg:
        password = client.set_secret("docker_password", os.environ["DOCKER_PASS"])
        base_image = build_image(client, "Dockerfile.build", password)
        await publish_image(base_image, f"build-latest")
        app_image = build_image(client, f"Dockerfile.{os.environ["ENV"]}", password)
        for tag in [commit_sha, "latest"]:
            tg.start_soon(publish_image, app_image, f"{os.environ["ENV"]}-{tag}")

def build_image(client: dagger.Client, image_path: str, password: dagger.Secret):
    return client.host().directory(".").docker_build(dockerfile=image_path).with_registry_auth(
        "docker.io",
        os.environ["DOCKER_USER"],
        password
    )

async def publish_image(image: dagger.Container, tag: str):
    await image.publish(f"{os.environ["DOCKER_REPO"]}:{tag}")

anyio.run(main)
