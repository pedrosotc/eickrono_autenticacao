# Etapa única esperando o artefato já construído via Maven (mvn package).
FROM eclipse-temurin:21-jre-alpine

ENV JAVA_OPTS=""
WORKDIR /app

# Copia o artefato gerado para dentro da imagem.
COPY target/api-identidade-eickrono-*.jar /app/aplicacao.jar

EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/aplicacao.jar"]
