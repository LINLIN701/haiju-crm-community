FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --create-home haiju
WORKDIR /app
COPY --from=build /workspace/target/haiju-crm-community-*.jar app.jar
USER haiju
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
