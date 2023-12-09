# Akka Backend TP

To be completed.


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
