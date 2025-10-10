FROM openjdk:25-jdk-slim

WORKDIR /app

COPY target/apitemplate-*.jar app.jar

# Make port 8080 available to the world outside this container
# (or whatever port your Spring Boot app runs on)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]