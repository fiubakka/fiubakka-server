# Akka Backend TP

An Akka server for handling Fiubakka connections.

## Dependencies
* [sbt](https://www.scala-sbt.org/download.html)
* [netstat](https://www.tecmint.com/install-netstat-in-linux/)
* [docker](https://docs.docker.com/engine/install/)

## How to run the server
### Using the docker image
  ```console
> docker run -p 2020:2020 mrmarcosrolando/fiubakka-server:latest
```


### Using the run script
After cloning the repository, the protobuf submodule needs to be initialized using the command:
`git submodule update --init --recursive`

That brings all the .proto files from another repository, but before using them they need to be compiled using
`sbt compile`

In the root directory (where the `docker-compose.yaml` file is located) run `docker compose up -d` to start the postgres db and kafka dependencies.

When the startup is completed you can run the first node of the server using the `run.sh` script. If you want to add more nodes, you need to run the same script with the parameters for the server port and the acceptor port. You can see more info by running `./run.sh --help`


## Running Lightbend Telemetry

From the root of the project, run:

```
./telemetry.sh
```

You can then access Grafana at http://localhost:3000 and Prometheus at
http://localhost:9090.
