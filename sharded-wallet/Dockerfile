# ---------- Build Stage ----------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom first — leverages Docker layer caching
# so dependencies aren't re-downloaded on every code change
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Now copy the actual source code and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ---------- Run Stage ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy only the built jar from the build stage — keeps final image small
COPY --from=build /app/target/*.jar app.jar

# Render injects PORT env var dynamically — our app must listen on it
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]