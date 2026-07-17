# Stage 1: Build the Maven application in backend subdirectory
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY backend ./backend
WORKDIR /app/backend
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime execution image
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/backend/target/*.jar app.jar

# Render automatically sets the PORT environment variable
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=${PORT}"]
