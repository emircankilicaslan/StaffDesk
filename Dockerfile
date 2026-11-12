FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 staffdesk
USER staffdesk
WORKDIR /app
COPY --from=build /src/target/staffdesk-1.0.0.jar app.jar
EXPOSE 8080
# DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET (en az 32 karakter) çalıştırırken verilmelidir.
ENTRYPOINT ["java", "-jar", "app.jar"]