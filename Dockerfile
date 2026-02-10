# Dockerfile (ejecuta: mvn clean compile gatling:test ...)
FROM maven:3.9-eclipse-temurin-17

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN chmod -R g+rwX,o+rX /app && chgrp -R 0 /app

# Variables por defecto (sobrescribibles en OpenShift)
ENV BASE_URL="https://botplatform.stg.telefonica.es/bot"
ENV REQ_CSV="/results/requests.csv"
ENV REQ_CSV_FLUSH_EVERY="2000"

# Directorio de salida
RUN mkdir -p /results

# (opcional) cachea dependencias para builds más rápidas
RUN mvn -q -DskipTests dependency:go-offline

# Ejecuta Gatling al arrancar el contenedor
ENTRYPOINT ["sh","-c", "mvn -q clean compile gatling:test -DbaseUrl=$BASE_URL -DreqCsv=$REQ_CSV -DreqCsvFlushEvery=$REQ_CSV_FLUSH_EVERY"]

