# Imagen del back. Sin secretos: la app lee DB_URL, DB_USER, DB_PASSWORD, GEOBCN_API_KEYS y GEOBCN_JWT_SECRET
# del entorno (en ECS, de Secrets Manager). Context-path /geo-bcn/v2 en el puerto 8080.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build --chown=app /app.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
