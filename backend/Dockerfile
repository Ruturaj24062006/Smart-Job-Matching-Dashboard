# Stage 1: Build the Maven application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Lightweight Ubuntu-based runtime image
# NOTE: Must use 'jammy' (Ubuntu 22.04), NOT 'alpine'
# Alpine uses musl libc which is missing libstdc++.so.6
# ONNX Runtime (used by LangChain4j embeddings) requires glibc (libstdc++)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/career-match-backend-0.0.1-SNAPSHOT.jar app.jar

# Render sets PORT automatically; activate prod profile
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", \
  "-Dspring.profiles.active=prod", \
  "-Dserver.port=${PORT}", \
  "-jar", "app.jar"]
