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
        build_image = client.host().directory('.').docker_build(target="dev").with_registry_auth(
            "docker.io",
            os.environ["DOCKER_USER"],
            password
        )
        for tag in [commit_sha, "latest"]:
            tg.start_soon(build_image.publish, f"{os.environ["DOCKER_REPO"]}:{tag}")

anyio.run(build)
