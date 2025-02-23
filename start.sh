#!/bin/bash

/home/fabiano/opt/jdk8u372-b07/bin/java --version

echo "First arg: $1"

java -jar ./target/loan-approval-spring-boot-0.0.1-SNAPSHOT.jar --server.port=$1