# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build final package
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage (Debian-based glibc environment fixes Conscrypt UnsatisfiedLinkError)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Security: run application as non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appuser /app/app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]