# Этап 1: Сборка
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Копируем файлы для кэширования зависимостей Gradle
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

# Даем права на выполнение и загружаем зависимости
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# Копируем исходники и собираем
COPY src src
# JTE обычно требует предкомпиляции, команда bootJar это учитывает
RUN ./gradlew generateJte bootJar --no-daemon -x test

RUN ./gradlew bootJar --no-daemon -x test

# Этап 2: Запуск
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копируем готовый jar
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
