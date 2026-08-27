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
    cp target/ch-vacd-api-reference-server-*.jar /ch-vacd-api-server.jar

# Stage 2 — runtime image
FROM bellsoft/liberica-openjdk-alpine:latest
EXPOSE 9111

COPY --from=build /ch-vacd-api-server.jar /ch-vacd-api-server.jar

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'
RUN mkdir -p /database /config

ENTRYPOINT ["java", "-Xmx1024M", \
  "-Dspring.config.additional-location=optional:file:/config/application.yaml,optional:file:application.yaml", \
  "-jar", "/ch-vacd-api-server.jar"]
