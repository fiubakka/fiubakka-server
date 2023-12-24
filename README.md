# Akka Backend TP

To be completed.

## Dependencies
* [sbt](https://www.scala-sbt.org/download.html)
* [docker](https://docs.docker.com/engine/install/)

## How to run the server
### Using the docker image
  ```console
> docker run -p 2020:2020 mrmarcosrolando/fiubakka-server:latest
```


### Using the run script
After cloning the repository, the protobuf submodule needs to be updated using the command:
`git submodule update --recursive --remote`

That brings all the .proto files from another repository, but before using them they need to be compiled using
`sbt compile`

In the root directory (where the `docker-compose.yaml` file is located) run `docker compose up [-d]` to start the postgres db and kafka dependencies.

When the startup is completed you can run the first node of the server using the `run.sh` script. If you want to add more nodes, you need to run the same script with the parameters for the server port and the accepter port. You can see more info by running `./run.sh --help`


## Running Lightbend Telemetry

From the root of the project, run:

```
./telemetry.sh
```

You can then access Grafana at http://localhost:3000 and Prometheus at
http://localhost:9090.

**IMPORTANT**: When running Telemetry locally, you can't instantiate more
than one node using the `./run.sh` script. It seems that running Telemetry
forks the process at the beginning and it does not pass the arguments to
the Akka application, ignoring the subsequent ports configuration.
