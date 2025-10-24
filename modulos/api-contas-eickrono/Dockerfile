# Dockerfile simples que consome o JAR construído previamente.
FROM eclipse-temurin:21-jre-alpine

ENV JAVA_OPTS=""
WORKDIR /app

COPY target/api-contas-eickrono-*.jar /app/aplicacao.jar

EXPOSE 8082 5006
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/aplicacao.jar"]
