#!/bin/bash
su - postgres -c "pg_ctl -D /var/lib/postgresql/data start"
exec java \
  -Dakka.cluster.seed-nodes.0=akka://game-system@127.0.0.1:25520 \
  -Dakka.remote.artery.canonical.port=25520 \
  -Dakka.remote.artery.bind.port=25520 \
  -Dplayer-accepter.port=9090 \
  -jar app.jar
