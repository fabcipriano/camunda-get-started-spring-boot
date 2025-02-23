FROM openjdk:8-jdk-alpine

RUN mkdir /opt/spring

ADD ./target/loan-approval-spring-boot-0.0.1-SNAPSHOT.jar /opt/spring

EXPOSE 8080

ENTRYPOINT java -jar /opt/spring/loan-approval-spring-boot-0.0.1-SNAPSHOT.jar