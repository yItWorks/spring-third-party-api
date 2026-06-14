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
    // 브라우저에서 Rate Limit 을 직접 두드려 보는 SSR 데모 페이지(checkout/success/fail/history) 렌더링용
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // 게이트웨이가 429 + Retry-After 를 내려주는 상황을 흉내내는 스텁 서버 (클라이언트 백오프 테스트용)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
