#jdk для запуска сервера
FROM openjdk:17-jdk-slim

#рабочая папка??
WORKDIR /app

# Если нужно, копируем файлы (для сборки образа, если volume не используется)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x gradlew

EXPOSE 8080

CMD ["./gradlew", "run"]
