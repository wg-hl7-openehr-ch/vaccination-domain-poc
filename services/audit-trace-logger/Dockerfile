# Stage 1 — build the Spring Boot fat jar with Maven
FROM bellsoft/liberica-openjdk-alpine:latest AS build
RUN apk add --no-cache maven
WORKDIR /src
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B && \
    cp target/audit-trace-logger-*.jar /audit-trace-logger.jar

# Stage 2 — runtime image
FROM bellsoft/liberica-openjdk-alpine:latest
EXPOSE 9113

COPY --from=build /audit-trace-logger.jar /audit-trace-logger.jar

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'
RUN mkdir -p /database /config

ENTRYPOINT ["java", "-Xmx1024M", \
  "-Dspring.config.additional-location=optional:file:/config/application.yaml,optional:file:application.yaml", \
  "-jar", "/audit-trace-logger.jar"]
  
  