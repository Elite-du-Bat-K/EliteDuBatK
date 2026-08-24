FROM maven:3-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests && \
	JAR_PATH="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | head -n 1)" && \
	cp "$JAR_PATH" target/app.jar

FROM eclipse-temurin:25-jre AS runner

WORKDIR /app

COPY --from=build /app/target/app.jar app.jar

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]