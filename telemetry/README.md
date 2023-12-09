# Lightbend Telemetry Sandbox

This module provides a sandbox for testing out the Lightbend Telemetry instrumentation.

This sandbox is a modified version of the one provided in https://developer.lightbend.com/docs/telemetry/current//sandbox/prometheus-sandbox.html.

I adapted it to work with this project, as the default configuration does not work out of the box.

The main change is using the host network for Grafana and Prometheus.

## Running the sandbox

From the root of the project, run:

```
docker compose -f linux/docker-compose.yml up -d
```
