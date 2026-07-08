# ---- Build stage -----------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Cache dependencies first (only re-runs when pom/wrapper change).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Build the jar (tests run in CI; skipped here to keep image builds fast).
COPY src ./src
RUN ./mvnw -B -ntp -DskipTests clean package

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user.
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/data/uploads && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

# Memory-capped for a 1 GB t3.micro. MaxRAMPercentage keeps the JVM within the
# container limit; override JAVA_OPTS to tune.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=40"
ENV SPRING_PROFILES_ACTIVE=prod

# Lightweight healthcheck against the actuator endpoint.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
