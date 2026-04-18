plugins {
	java
	id("org.springframework.boot") version "3.4.3"
	id("io.spring.dependency-management") version "1.1.7"
	id("gg.jte.gradle") version "3.1.16"
}

group = "dev.c2"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(23)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("io.github.tors42:chariot:0.1.21")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("gg.jte:jte:3.1.16")
	implementation("gg.jte:jte-spring-boot-starter-3:3.1.16")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

jte {
	generate()
	binaryStaticContent = true
}

tasks.withType<Test> {
	useJUnitPlatform()
}
