# Third-party notices

Uptrail is built on open-source components. This file lists them with the licence each project declares in its published Maven metadata (collected with `./mvnw dependency:list` from the POM files on 2026-09-28). The full licence texts are published by each project. Versions are those resolved by this build.

## Included in the application JAR

These libraries are packaged into `target/uptrail-<version>.jar`.

| Group | Artifacts | Version | License (as declared by the project) |
| --- | --- | --- | --- |
| `ch.qos.logback` | logback-classic, logback-core | 1.5.38 | EPL-2.0; LGPL-2.1-only |
| `com.fasterxml` | classmate | 1.7.3 | Apache-2.0 |
| `com.fasterxml.jackson.core` | jackson-annotations | 2.21 | Apache-2.0 |
| `com.mysql` | mysql-connector-j | 9.7.0 | The GNU General Public License, v2 with Universal FOSS Exception, v1.0 |
| `com.sun.istack` | istack-commons-runtime | 4.1.2 | Eclipse Distribution License - v 1.0 |
| `com.zaxxer` | HikariCP | 7.0.2 | Apache-2.0 |
| `commons-logging` | commons-logging | 1.3.6 | Apache-2.0 |
| `io.micrometer` | micrometer-commons, micrometer-core, micrometer-jakarta9, micrometer-observation | 1.17.1 | Apache-2.0 |
| `jakarta.activation` | jakarta.activation-api | 2.1.4 | EDL 1.0 |
| `jakarta.annotation` | jakarta.annotation-api | 3.0.0 | EPL 2.0; GPL2 w/ CPE |
| `jakarta.inject` | jakarta.inject-api | 2.0.1 | Apache-2.0 |
| `jakarta.mail` | jakarta.mail-api | 2.1.5 | EPL 2.0; GPL2 w/ CPE; EDL 1.0 |
| `jakarta.persistence` | jakarta.persistence-api | 3.2.0 | Eclipse Public License v. 2.0; Eclipse Distribution License v. 1.0 |
| `jakarta.transaction` | jakarta.transaction-api | 2.0.1 | EPL 2.0; GPL2 w/ CPE |
| `jakarta.validation` | jakarta.validation-api | 3.1.1 | Apache-2.0 |
| `jakarta.xml.bind` | jakarta.xml.bind-api | 4.0.5 | Eclipse Distribution License - v 1.0 |
| `net.bytebuddy` | byte-buddy | 1.18.11 | Apache-2.0 |
| `org.antlr` | antlr4-runtime | 4.13.2 | BSD-3-Clause |
| `org.apache.logging.log4j` | log4j-api, log4j-to-slf4j | 2.25.5 | Apache-2.0 |
| `org.apache.tomcat.embed` | tomcat-embed-core, tomcat-embed-el, tomcat-embed-websocket | 11.0.24 | Apache-2.0 |
| `org.aspectj` | aspectjweaver | 1.9.25.1 | Eclipse Public License - v 2.0 |
| `org.attoparser` | attoparser | 2.0.7.RELEASE | Apache-2.0 |
| `org.eclipse.angus` | angus-activation | 2.0.3 | EDL 1.0 |
| `org.eclipse.angus` | angus-mail | 2.0.5 | EPL 2.0; GPL2 w/ CPE; EDL 1.0 |
| `org.flywaydb` | flyway-core, flyway-mysql | 12.4.0 | Apache-2.0 |
| `org.glassfish.jaxb` | jaxb-core, jaxb-runtime, txw2 | 4.0.9 | Eclipse Distribution License - v 1.0 |
| `org.hdrhistogram` | HdrHistogram | 2.2.2 | Public Domain, per Creative Commons CC0; BSD-2-Clause |
| `org.hibernate.models` | hibernate-models | 1.1.1 | Apache-2.0 |
| `org.hibernate.orm` | hibernate-core | 7.4.5.Final | Apache-2.0 |
| `org.hibernate.validator` | hibernate-validator | 9.1.3.Final | Apache-2.0 |
| `org.jboss.logging` | jboss-logging | 3.6.3.Final | Apache-2.0 |
| `org.jspecify` | jspecify | 1.0.1 | Apache-2.0 |
| `org.slf4j` | jul-to-slf4j, slf4j-api | 2.0.18 | MIT |
| `org.springframework` | spring-aop, spring-aspects, spring-beans, spring-context, spring-context-support, spring-core, spring-expression, spring-jdbc, spring-orm, spring-tx, spring-web, spring-webmvc | 7.0.9 | Apache-2.0 |
| `org.springframework.boot` | spring-boot, spring-boot-actuator, spring-boot-actuator-autoconfigure, spring-boot-autoconfigure, spring-boot-data-commons, spring-boot-data-jpa, spring-boot-flyway, spring-boot-health, spring-boot-hibernate, spring-boot-http-converter, spring-boot-jackson, spring-boot-jdbc, spring-boot-jpa, spring-boot-mail, spring-boot-micrometer-metrics, spring-boot-micrometer-observation, spring-boot-persistence, spring-boot-security, spring-boot-servlet, spring-boot-sql, spring-boot-starter, spring-boot-starter-actuator, spring-boot-starter-data-jpa, spring-boot-starter-flyway, spring-boot-starter-jackson, spring-boot-starter-jdbc, spring-boot-starter-logging, spring-boot-starter-mail, spring-boot-starter-micrometer-metrics, spring-boot-starter-security, spring-boot-starter-thymeleaf, spring-boot-starter-tomcat, spring-boot-starter-tomcat-runtime, spring-boot-starter-validation, spring-boot-starter-webmvc, spring-boot-thymeleaf, spring-boot-tomcat, spring-boot-transaction, spring-boot-validation, spring-boot-web-server, spring-boot-webmvc | 4.1.1 | Apache-2.0 |
| `org.springframework.data` | spring-data-commons, spring-data-jpa | 4.1.1 | Apache-2.0 |
| `org.springframework.security` | spring-security-config, spring-security-core, spring-security-crypto, spring-security-web | 7.1.1 | Apache-2.0 |
| `org.thymeleaf` | thymeleaf, thymeleaf-spring6 | 3.1.5.RELEASE | Apache-2.0 |
| `org.unbescape` | unbescape | 1.1.6.RELEASE | Apache-2.0 |
| `org.webjars.npm` | bootstrap | 5.3.8 | MIT |
| `org.yaml` | snakeyaml | 2.6 | Apache-2.0 |
| `tools.jackson.core` | jackson-core, jackson-databind | 3.1.5 | Apache-2.0 |

Notes:

- `org.webjars.npm:bootstrap` contains Bootstrap 5.3.8 (CSS and the JavaScript bundle, which includes Popper). Both are MIT licensed.
- `com.mysql:mysql-connector-j` is licensed under the GPL v2 with the Universal FOSS Exception; see the Connector/J licence for its conditions.
- Several Jakarta and Eclipse artifacts declare more than one licence; see each project for how the licences apply.

## Used only for building and testing

These are not packaged into the application.

| Group | Artifacts | Version | License (as declared by the project) |
| --- | --- | --- | --- |
| `com.github.docker-java` | docker-java-api, docker-java-transport, docker-java-transport-zerodep | 3.7.1 | Apache-2.0 |
| `com.icegreen` | greenmail, greenmail-junit5 | 2.1.14 | Apache-2.0 |
| `com.jayway.jsonpath` | json-path | 2.10.0 | Apache-2.0 |
| `com.tngtech.archunit` | archunit | 1.5.1 | Apache-2.0; BSD |
| `com.tngtech.archunit` | archunit-junit5, archunit-junit5-api, archunit-junit5-engine, archunit-junit5-engine-api | 1.5.1 | Apache-2.0 |
| `com.vaadin.external.google` | android-json | 0.0.20131108.vaadin1 | Apache-2.0 |
| `commons-codec` | commons-codec | 1.21.0 | Apache-2.0 |
| `commons-io` | commons-io | 2.20.0 | Apache-2.0 |
| `junit` | junit | 4.13.2 | Eclipse Public License 1.0 |
| `net.bytebuddy` | byte-buddy-agent | 1.18.11 | Apache-2.0 |
| `net.java.dev.jna` | jna | 5.18.1 | LGPL-2.1-or-later; Apache-2.0 |
| `net.minidev` | accessors-smart, json-smart | 2.6.0 | Apache-2.0 |
| `org.apache.commons` | commons-compress | 1.28.0 | Apache-2.0 |
| `org.apache.commons` | commons-lang3 | 3.20.0 | Apache-2.0 |
| `org.apiguardian` | apiguardian-api | 1.1.2 | Apache-2.0 |
| `org.assertj` | assertj-core | 3.27.7 | Apache-2.0 |
| `org.awaitility` | awaitility | 4.3.0 | Apache-2.0 |
| `org.eclipse.angus` | jakarta.mail | 2.0.5 | EPL 2.0; GPL2 w/ CPE; EDL 1.0 |
| `org.hamcrest` | hamcrest | 3.0 | BSD-3-Clause |
| `org.jetbrains` | annotations | 17.0.0 | Apache-2.0 |
| `org.junit.jupiter` | junit-jupiter, junit-jupiter-api, junit-jupiter-engine, junit-jupiter-params | 6.0.3 | Eclipse Public License v2.0 |
| `org.junit.platform` | junit-platform-commons, junit-platform-engine | 6.0.3 | Eclipse Public License v2.0 |
| `org.mockito` | mockito-core, mockito-junit-jupiter | 5.23.0 | MIT |
| `org.objenesis` | objenesis | 3.3 | Apache-2.0 |
| `org.opentest4j` | opentest4j | 1.3.0 | Apache-2.0 |
| `org.ow2.asm` | asm | 9.7.1 | BSD-3-Clause |
| `org.rnorth.duct-tape` | duct-tape | 1.0.8 | MIT |
| `org.skyscreamer` | jsonassert | 1.5.3 | Apache-2.0 |
| `org.springframework` | spring-test | 7.0.9 | Apache-2.0 |
| `org.springframework.boot` | spring-boot-resttestclient, spring-boot-security-test, spring-boot-starter-jackson-test, spring-boot-starter-security-test, spring-boot-starter-test, spring-boot-starter-webmvc-test, spring-boot-test, spring-boot-test-autoconfigure, spring-boot-webmvc-test | 4.1.1 | Apache-2.0 |
| `org.springframework.security` | spring-security-test | 7.1.1 | Apache-2.0 |
| `org.testcontainers` | testcontainers, testcontainers-database-commons, testcontainers-jdbc, testcontainers-mysql | 2.0.5 | MIT |
| `org.xmlunit` | xmlunit-core | 2.11.0 | Apache-2.0 |

Profile-only tools:

| Component | Version | License (as declared by the project) | Used by |
| --- | --- | --- | --- |
| `com.microsoft.playwright:playwright` (with its browser driver) | 1.63.0 | Apache-2.0 | `./mvnw -Pe2e verify` |
| `net.sourceforge.plantuml:plantuml-mit` | 1.2026.8 | MIT | `./mvnw -Pdiagrams generate-resources` |

Playwright downloads a Chromium build on first use; Chromium is distributed under its own licence terms.

## Demo image

`Dockerfile.vercel` builds one image from these public images; the image is used for the public demo and for running Uptrail with Docker only.

| Component | Source image | License |
| --- | --- | --- |
| MySQL Community Server 8.4 | `mysql:8.4` | GPL-2.0 |
| Eclipse Temurin Java 21 runtime | `eclipse-temurin:21-jre` (build: `eclipse-temurin:21-jdk`) | GPL-2.0 with Classpath Exception |
| Caddy web server | `caddy:2` | Apache-2.0 |

## Local development services

Docker images started by `docker-compose.yml`; they are not part of Uptrail.

| Image | License |
| --- | --- |
| `mysql:8.4` (MySQL Community Server) | GPL-2.0 |
| `axllent/mailpit:v1.31` | MIT |
