FROM openjdk:8-jdk-alpine

RUN mkdir /opt/spring

ADD ./target/loan-approval-spring-boot-0.0.1-SNAPSHOT.jar /opt/spring

# Expose both ports 8080 and 1099
EXPOSE 8080 1099

ENTRYPOINT java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=1099 -Dcom.sun.management.jmxremote.rmi.port=1099 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -jar /opt/spring/loan-approval-spring-boot-0.0.1-SNAPSHOT.jar