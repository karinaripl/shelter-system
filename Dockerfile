# syntax=docker/dockerfile:1
#
# Один Dockerfile для всех Java-модулей проекта.
# Какой модуль упаковать в образ, задаётся аргументом MODULE (см. docker-compose.yml).
#
# Этап build одинаковый для всех сервисов, поэтому Docker собирает проект один раз
# и переиспользует результат из кэша. Скачанные Maven-зависимости сохраняются
# в кэше BuildKit и не скачиваются заново при следующих сборках.

# ---------- сборка ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -DskipTests package

# ---------- запуск ----------
FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
