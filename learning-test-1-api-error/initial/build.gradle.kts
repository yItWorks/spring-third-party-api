plugins {
    java
    // 버전은 루트 build.gradle.kts에서 관리한다.
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "woowacourse"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // 브라우저에서 Toss 결제창을 띄우는 SSR 페이지(checkout/success/fail) 렌더링용
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Toss API를 흉내내는 스텁 서버 (에러코드/지연/상태코드 주입용)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
