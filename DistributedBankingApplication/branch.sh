#!/bin/bash +vx
LIB_PATH=$"./lib/protobuf-java-3.6.1.jar"
java -classpath bin:$LIB_PATH Branch $1 $2 $3
