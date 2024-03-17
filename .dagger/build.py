import dagger
import anyio
import subprocess
import os

async def build():
    subprocess.run(["git", "submodule", "update", "--init"])

    for env_var in ["DOCKER_USER", "DOCKER_PASS", "DOCKER_REPO"]:
        if env_var not in os.environ:
            raise OSError(f"{env_var} environment variable must be set")
    commit_sha = subprocess.check_output(["git", "rev-parse", "HEAD"]).strip().decode("utf-8")[:7]

    async with dagger.Connection() as client, anyio.create_task_group() as tg:
        password = client.set_secret("docker_password", os.environ["DOCKER_PASS"])
        build_image = client.host().directory(".").docker_build(dockerfile="Dockerfile.build").with_registry_auth(
            "docker.io",
            os.environ["DOCKER_USER"],
            password
        )
        await publish_image(build_image, "build-latest")
        app_image = client.host().directory('.').docker_build(dockerfile=f"Dockerfile.{os.environ["ENV"]}").with_registry_auth(
            "docker.io",
            os.environ["DOCKER_USER"],
            password
        )
        for tag in [commit_sha, f"{os.environ["ENV"]}-latest"]:
            tg.start_soon(publish_image, app_image, tag)

async def publish_image(image: dagger.Container, tag: str):
    return image.publish(f"{os.environ["DOCKER_REPO"]}:{tag}")


anyio.run(build)
