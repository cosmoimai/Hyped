# Build the JAR with ./mvnw clean verify first. Only that artifact enters this build.
FROM eclipse-temurin:21-jdk-jammy@sha256:742daacb9316be4b6fc60258f6e4ac2a11882f6c597373c92290a57bfad0d2e0 AS extract
WORKDIR /extract
COPY target/hyped-backend-*.jar application.jar
RUN jar xf application.jar BOOT-INF/classes BOOT-INF/lib

FROM eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0
WORKDIR /opt/hyped
COPY --from=extract /extract/BOOT-INF/classes ./classes
COPY --from=extract /extract/BOOT-INF/lib ./lib
ENV SPRING_FLYWAY_ENABLED=false SERVER_ADDRESS=0.0.0.0
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-cp", "/opt/hyped/classes:/opt/hyped/lib/*", "com.hyped.app.HypedApplication"]
