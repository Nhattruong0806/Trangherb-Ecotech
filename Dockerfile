FROM maven:3.9.6-eclipse-temurin-17

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests

CMD ["java", "-jar", "target/ecotech-chatbot-1.0.0.jar"]
