# Multi-stage Dockerfile for CareerPilot Spring Boot Backend

# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# Install dos2unix to ensure mvnw line endings don't cause issues
RUN apk add --no-cache dos2unix

# Copy Maven wrapper and POM for dependency layer caching
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN dos2unix mvnw && chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source and package executable JAR
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Production JRE runtime
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Install wget for container health checks
RUN apk add --no-cache wget ca-certificates

# Security: Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy artifact from builder
COPY --from=builder --chown=spring:spring /workspace/target/*.jar app.jar

USER spring:spring

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
