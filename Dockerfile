FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/payment-reliability-api-0.1.0.jar app.jar
EXPOSE 8080
# Default cloud image to the disposable demo ledger. This system property
# overrides an old SPRING_PROFILES_ACTIVE=postgres environment setting.
# Override only with PAYMENTS_DEPLOY_PROFILE=postgres when a database is configured.
ENTRYPOINT ["sh", "-c", "exec java -Dspring.profiles.active=${PAYMENTS_DEPLOY_PROFILE:-demo} -jar app.jar"]
