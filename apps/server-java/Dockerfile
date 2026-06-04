FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package \
  && JAR_PATH="$(find target -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1)" \
  && test -n "$JAR_PATH" \
  && cp "$JAR_PATH" target/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=Asia/Shanghai

COPY --from=builder /workspace/target/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--XX:MaxRAMPercentage=75 -XX:+UseG1GC} -jar /app/app.jar"]
