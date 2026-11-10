# Etapa 1: compilar el jar con Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Etapa 2: imagen liviana solo con el JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV STORAGE_DIR=/app/storage
RUN mkdir -p /app/storage
# Render asigna el puerto en la variable PORT (application.yml ya la lee)
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
