# Step 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src
# Build the JAR file
RUN mvn clean package -DskipTests

# Step 2: Run the application
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache imagemagick

# Copy the built JAR from the previous stage
COPY --from=build /app/target/*.jar app.jar
# Expose the port
EXPOSE 8080
# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]