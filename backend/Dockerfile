FROM node:22-alpine AS frontend-build

WORKDIR /frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend-build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/src/ backend/src/
COPY --from=frontend-build /frontend/dist/ backend/src/main/resources/static/

RUN mvn -B -ntp -f backend/pom.xml clean package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=backend-build /workspace/backend/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
