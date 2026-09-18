# Stage 1 — build the Spring Boot fat jar with Maven
FROM bellsoft/liberica-openjdk-alpine:latest AS build
RUN apk add --no-cache maven
WORKDIR /src
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw -U package -DskipTests -B && \
    cp target/bff-producer-server-*.jar /bff-producer-server.jar

# Stage 2 — runtime image
FROM bellsoft/liberica-openjdk-alpine:latest
EXPOSE 9112

COPY --from=build /bff-producer-server.jar /bff-producer-server.jar

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

ENTRYPOINT ["java", "-Xmx1024M", \
  "-Dspring.config.additional-location=optional:file:/config/application.yaml,optional:file:application.yaml", \
  "-jar", "/bff-producer-server.jar"]
