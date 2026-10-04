# Stage 1: Build
FROM --platform=$BUILDPLATFORM maven:3-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:22-jre-jammy
WORKDIR /app

# Create standard directories
RUN mkdir -p config data logs

# Copy the built jar
COPY --from=build /app/target/fixtestdriver-*.jar app.jar

# Standard Volume Mounts
VOLUME ["/app/config", "/app/data", "/app/logs"]

# API and WebSocket port
EXPOSE 8080

# Default FIX ports (Initiator/Acceptor)
EXPOSE 9878 9880 9888

# Environment Variable Defaults
ENV CONFIG_ROOT=/app/config
ENV FIX_CONFIG_PATH=/app/config/FixEngine.cfg
ENV TESTCASES_ROOT=/app/config/testcases
ENV PRODUCTS_DEF_PATH=/app/config/products.def
ENV QUICKFIX_DICT_PATH=/app/config/quickfix

ENTRYPOINT ["java", "-jar", "app.jar"]
