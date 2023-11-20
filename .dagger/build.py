import dagger
import anyio

async def build():
    async with dagger.Connection() as client:
        await client.host().directory('.').docker_build(
            target='dev',
        )

anyio.run(build)
