FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# JDK (not just JRE) because submitted Java code is compiled with javac at
# request time, plus gcc/g++/python3/node for the other supported languages.
FROM eclipse-temurin:17-jdk
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        python3 \
        nodejs \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/compiler-data-service-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
