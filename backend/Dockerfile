# Stage 1: Build the Maven application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Create the runtime execution image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/career-match-backend-0.0.1-SNAPSHOT.jar app.jar

# Render automatically sets the PORT environment variable
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=${PORT}"]
