LIB_PATH=./lib/protobuf-java-3.6.1.jar:./lib/slf4j-log4j12-1.7.12.jar:./lib/slf4j-api-1.7.12.jar:./lib/log4j-1.2.17.jar:./lib/javax.annotation.jar
all: clean
	mkdir bin
	javac -classpath $(LIB_PATH) -d bin/ src/RequestHandler.java src/Branch.java src/Bank.java src/Controller.java
clean:
	rm -rf bin/

