# Етап 1: Збірка (Build)
# Використовуємо образ Maven з JDK 17 для компіляції проекту
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# Встановлюємо робочу директорію всередині контейнера
WORKDIR /app

# Копіюємо файл налаштувань Maven та залежностей
COPY pom.xml .

# Завантажуємо залежності (щоб закешувати цей шар і пришвидшити майбутні збірки)
RUN mvn dependency:go-offline

# Копіюємо вихідний код проекту
COPY src ./src

# Збираємо проект, пропускаючи тести (тести краще запускати окремо в CI/CD)
RUN mvn clean package -DskipTests

# Етап 2: Запуск (Run)
# Використовуємо легкий образ JRE 17 для запуску готового додатку
FROM eclipse-temurin:17-jre-alpine

# Копіюємо mapping.json поки ми ще root, щоб створити папку /config без помилок
COPY sharding-config/mapping.json /config/mapping.json

# Створюємо користувача для безпеки (щоб не запускати під root)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Встановлюємо робочу директорію
WORKDIR /app

# Копіюємо зібраний JAR-файл з етапу збірки
COPY --from=builder /app/target/*.jar app.jar

# Відкриваємо порт 8080
EXPOSE 8080

# Команда запуску
ENTRYPOINT ["java", "-jar", "app.jar"]