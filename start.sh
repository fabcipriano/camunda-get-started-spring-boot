#!/bin/bash

/home/fabiano_oliveira/opt/jdk8u402-b06/bin/java -version
echo "First arg: $1"

/home/fabiano_oliveira/opt/jdk8u402-b06/bin/java -jar ./target/loan-approval-spring-boot-0.0.1-SNAPSHOT.jar --server.port=$1