# Builder
FROM hseeberger/scala-sbt:eclipse-temurin-11.0.14.1_1.6.2_2.13.8 as builder

WORKDIR /usr/app

COPY ./lightbend.sbt .
COPY ./build.sbt .
COPY ./project project/

# Install dependencies
RUN sbt update

COPY ./src src/

# Generate JAR bundle
RUN sbt clean assembly


# Production
FROM eclipse-temurin:11.0.21_9-jre-jammy as prod

WORKDIR /usr/app

COPY --from=builder /usr/app/target/scala-2.13/fiubakka-server-assembly-0.1.0-SNAPSHOT.jar /usr/app/app.jar

EXPOSE 8080/tcp

CMD ["java", "-jar", "app.jar"]


# See https://luppeng.wordpress.com/2020/02/28/install-and-start-postgresql-on-alpine-linux/
# for reference on PostgreSQL installation on Alpine Linux

# See https://kafka.apache.org/quickstart
# for reference on Kafka installation

# Development
FROM prod as dev

# Install PostgreSQL

RUN apk update && apk add --no-cache postgresql curl

RUN mkdir /run/postgresql && \
    chown postgres:postgres /run/postgresql && \
    su - postgres -c " \
        mkdir /var/lib/postgresql/data && \
        chmod 0700 /var/lib/postgresql/data && \
        initdb -D /var/lib/postgresql/data && \
        echo 'host all all 0.0.0.0/0 md5' >> /var/lib/postgresql/data/pg_hba.conf && \
        echo \"listen_addresses='*'\" >> /var/lib/postgresql/data/postgresql.conf \
    "

COPY ./.docker/akka.sql /var/lib/postgresql/data/akka.sql
COPY ./.docker/durable_state.sql /var/lib/postgresql/data/durable_state.sql
COPY ./.docker/players.sql /var/lib/postgresql/data/players.sql

RUN su - postgres -c " \
        pg_ctl -D /var/lib/postgresql/data start && \
        psql -f /var/lib/postgresql/data/akka.sql && \
        PGPASSWORD=akka psql -U akka -d akka -f /var/lib/postgresql/data/durable_state.sql \
        PGPASSWORD=akka psql -U akka -d akka -f /var/lib/postgresql/data/players.sql \
    "

# Install Kafka

RUN curl https://dlcdn.apache.org/kafka/3.6.1/kafka_2.13-3.6.1.tgz -o kafka_2.13-3.6.1.tgz \
    && tar -xzf kafka_2.13-3.6.1.tgz \
    && cd kafka_2.13-3.6.1 \
    && KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)" \
    && bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/kraft/server.properties \
    && bin/kafka-server-start.sh -daemon config/kraft/server.properties \
    && bin/kafka-topics.sh --create --topic game-zone --partitions 3 --bootstrap-server localhost:9092

EXPOSE 5432/tcp

ENV KAFKA_BOOTSTRAP_SERVERS=localhost:9092

COPY ./.docker/dev-entrypoint.sh /

CMD ["/dev-entrypoint.sh"]
