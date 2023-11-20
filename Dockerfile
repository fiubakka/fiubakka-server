# Builder
FROM hseeberger/scala-sbt:eclipse-temurin-11.0.14.1_1.6.2_2.13.8 as builder

WORKDIR /usr/app

COPY ./build.sbt .
COPY ./project project/

# Install dependencies
RUN sbt update

COPY ./src src/

# Generate JAR bundle
RUN sbt clean assembly


# Production
FROM eclipse-temurin:11.0.21_9-jre-alpine as prod

WORKDIR /usr/app

COPY --from=builder /usr/app/target/scala-2.13/akka-backend-tp-assembly-0.1.0-SNAPSHOT.jar /usr/app/app.jar

EXPOSE 8080/tcp

CMD ["java", "-jar", "app.jar"]


# See https://luppeng.wordpress.com/2020/02/28/install-and-start-postgresql-on-alpine-linux/
# for reference of PostgreSQL on Alpine Linux

# Development
FROM prod as dev

RUN apk update && apk add --no-cache postgresql

RUN mkdir /run/postgresql && \
    chown postgres:postgres /run/postgresql && \
    su - postgres -c " \
        mkdir /var/lib/postgresql/data && \
        chmod 0700 /var/lib/postgresql/data && \
        initdb -D /var/lib/postgresql/data && \
        echo 'host all all 0.0.0.0/0 md5' >> /var/lib/postgresql/data/pg_hba.conf && \
        echo \"listen_addresses='*'\" >> /var/lib/postgresql/data/postgresql.conf \
    "

COPY ./.docker/dev-entrypoint.sh /
COPY ./.docker/akka.sql /var/lib/postgresql/data/akka.sql
COPY ./.docker/durable_state.sql /var/lib/postgresql/data/durable_state.sql

RUN su - postgres -c " \
        pg_ctl -D /var/lib/postgresql/data start && \
        psql -f /var/lib/postgresql/data/akka.sql && \
        PGPASSWORD=akka psql -U akka -d akka -f /var/lib/postgresql/data/durable_state.sql \
    "

EXPOSE 5432/tcp

CMD ["/dev-entrypoint.sh"]
