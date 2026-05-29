plugins {
	java
	id("org.springframework.boot") version "3.4.3"
	id("io.spring.dependency-management") version "1.1.7"
	id("gg.jte.gradle") version "3.1.16"
}

group = "dev.rightknight"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
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
    implementation ("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation ("org.springframework.boot:spring-boot-starter")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
    runtimeOnly ("org.postgresql:postgresql")
    implementation ("org.flywaydb:flyway-core")
    implementation ("org.flywaydb:flyway-database-postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testRuntimeOnly("com.h2database:h2")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
}

jte {
    sourceDirectory = project.file("src/main/jte").toPath()

	generate()
	binaryStaticContent = true
}

tasks.withType<Test> {
	useJUnitPlatform()
}
