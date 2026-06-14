plugins {
    // 루트는 실행 대상이 아니라 모듈을 묶는 역할만 한다. 플러그인 버전만 정의하고 적용은 각 모듈에 위임한다.
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "woowacourse"
version = "0.0.1-SNAPSHOT"
description = "spring-third-party-api"
