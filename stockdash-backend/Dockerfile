FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY stockdash-backend stockdash-backend
COPY stockdash-frontend stockdash-frontend

RUN chmod +x gradlew && ./gradlew --no-daemon :stockdash-backend:bootJar

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /workspace/stockdash-backend/build/libs/*.jar app.jar

ENV PORT=18090
ENV SPRING_PROFILES_ACTIVE=mysql
ENV STOCKDASH_SEED_ENABLED=false

EXPOSE 18090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
