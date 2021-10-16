FROM adoptopenjdk/openjdk11:alpine

WORKDIR /

COPY target/thoughts-clj-0.0.1-SNAPSHOT-standalone.jar thoughts-clj-0.0.1-SNAPSHOT-standalone.jar

EXPOSE 3000

CMD java -jar thoughts-clj-0.0.1-SNAPSHOT-standalone.jar