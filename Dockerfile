# 1. Build Stage: Use a heavy image with Gradle to compile the code
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew installDist

# 2. Run Stage: Use a tiny image (Alpine Linux) to run the app
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy the compiled "install" folder from the builder stage
COPY --from=builder /app/app/build/install/app .

# Expose the gRPC port
EXPOSE 9090

# Command to run the server
CMD ["./bin/app"]