build:
	./gradlew clean build

install:
	./gradlew installDist

run-dist:
	./build/install/app/bin/app

lint:
	./gradlew checkstyleMain checkstyleTest

test:
	./gradlew test

report:
	./gradlew jacocoTestReport

.PHONY: build
