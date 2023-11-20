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


# Runner
FROM eclipse-temurin:11.0.21_9-jre-alpine

WORKDIR /usr/app

COPY --from=builder /usr/app/target/scala-2.13/akka-backend-tp-assembly-0.1.0-SNAPSHOT.jar /usr/app/app.jar

CMD ["java", "-jar", "app.jar"]
