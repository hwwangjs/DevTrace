
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN mvn -B -ntp clean package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]