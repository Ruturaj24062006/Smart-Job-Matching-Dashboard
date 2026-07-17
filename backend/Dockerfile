# Stage 1: Build the Maven application
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime execution image
FROM openjdk:17-jdk-slim
WORKDIR /app
# Copy the exact packaged JAR file to prevent multiple wildcard matches errors
COPY --from=build /app/target/career-match-backend-0.0.1-SNAPSHOT.jar app.jar

# Render automatically sets the PORT environment variable
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=${PORT}"]
