FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/ ./
COPY --from=frontend-build /workspace/frontend/dist/frontend/browser/ ./src/main/resources/static/
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bizlama && adduser -S bizlama -G bizlama
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/bizlama-api-0.0.1-SNAPSHOT.jar app.jar
USER bizlama
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]