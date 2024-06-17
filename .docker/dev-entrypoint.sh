#!/bin/bash

kafka_2.13-3.6.2/bin/kafka-server-start.sh -daemon kafka_2.13-3.6.2/config/kraft/server.properties

su - postgres -c "pg_ctl -D /var/lib/postgresql/data start"

export JAVA_OPTS="--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"

exec ./stage/bin/fiubakka-server \
  -Dakka.cluster.seed-nodes.0=akka://fiubakka-server@127.0.0.1:25520 \
  -Dakka.remote.artery.canonical.port=25520 \
  -Dakka.remote.artery.bind.port=25520 \
  -Dplayer-accepter.port=2020 \
