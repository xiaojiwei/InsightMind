# DA Mainline Dependency Pruning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Permanently remove first-round heavy non-core DA runtime dependencies while preserving graph, datasource query, dashboard, dimension value, and drill-down flows.

**Architecture:** Remove dependency declarations first, then replace direct integrations with dependency-free local behavior or neutral fallbacks. Use Maven compilation to expose remaining imports and apply mechanical annotation/import cleanup without changing API paths or request/response models.

**Tech Stack:** Spring Boot 2.5.6, Java 8, Maven, MyBatis/MyBatis-Plus, Jena, Logback, local file IO.

**Spec:** `docs/superpowers/specs/2026-09-01-da-mainline-dependency-pruning-design.md`

## Global Constraints

- Permanently remove clearly heavy, non-core, non-drill-down dependencies from the mainline DA build.
- Do not introduce a demo-only Maven profile as the main solution.
- Do not remove Spring Boot, Spring Web, JDBC, MyBatis/MyBatis-Plus, Druid, dynamic-datasource, MySQL driver, Jackson/Fastjson, Lombok, Guava, Hutool, Jena, ANTLR, validation, or test infrastructure in the first round.
- Do not remove POI or JPA/Hibernate in the first round.
- Do not remove Ansj, HanLP, ICU4J, Mahout, or Calcite in the first round.
- Do not change endpoint paths, request models, response models, database schemas, or credentials.
- Do not revert unrelated dirty working tree changes.

---

### Task 1: Prune Maven Runtime Dependencies

**Files:**
- Modify: `apps/da/pom.xml`

**Interfaces:**
- Consumes: current Maven dependency declarations.
- Produces: a mainline Maven build without first-round removed dependency declarations.

- [x] **Step 1: Remove dependency blocks**

Remove these exact dependency blocks from `apps/da/pom.xml`:

```xml
io.micrometer:micrometer-registry-prometheus
org.springframework.boot:spring-boot-starter-actuator
io.springfox:springfox-swagger2
io.springfox:springfox-swagger-ui
org.apache.skywalking:apm-toolkit-logback-1.x
net.bull.javamelody:javamelody-core
org.apache.skywalking:apm-toolkit-trace
com.baidubce:bce-java-sdk
com.google.protobuf:protobuf-java
org.apache.zookeeper:zookeeper
net.sf.cssbox:cssbox
org.seleniumhq.selenium:selenium-java
com.h2database:h2
com.hankcs.hanlp.restful:hanlp-restful
```

- [x] **Step 2: Verify removed artifacts are absent from `pom.xml`**

Run:

```bash
rg -n 'micrometer-registry-prometheus|spring-boot-starter-actuator|springfox|skywalking|javamelody|bce-java-sdk|protobuf-java|zookeeper|cssbox|selenium-java|h2database|hanlp-restful' apps/da/pom.xml
```

Expected: no output.

### Task 2: Remove BOS Runtime Integration

**Files:**
- Modify: `apps/da/src/main/java/com/graphinsight/indicator/service/impl/BosFileServiceImpl.java`
- Modify: `apps/da/src/main/java/com/graphinsight/indicator/model/FileTask.java`
- Delete: `apps/da/src/main/java/com/graphinsight/indicator/service/impl/BosUtils.java`
- Delete: `apps/da/src/main/java/com/graphinsight/indicator/model/BosFileClientTuple.java`

**Interfaces:**
- Consumes: `BosFileService.downloadBosFile(HttpServletResponse, String)` and `downloadBosFile(HttpServletResponse, String, String)`.
- Produces: `BosFileServiceImpl.writeBos(String fileName): String` that returns the local file name, and `downloadBosFile` that streams an existing local file or returns HTTP 404.

- [x] **Step 1: Replace `BosFileServiceImpl` with dependency-free local streaming**

Implementation shape:

```java
public static String writeBos(String fileName) {
    log.info("file export retained locally: {}", fileName);
    return fileName;
}

public void downloadBosFile(HttpServletResponse response, String fileName) throws IOException {
    Path filePath = Paths.get(fileName).normalize();
    if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"file not found in local export storage\"}");
        return;
    }
    response.setHeader("Content-Disposition", "attachment; filename=\"" + filePath.getFileName().toString() + "\"");
    Files.copy(filePath, response.getOutputStream());
    response.flushBuffer();
}
```

- [x] **Step 2: Remove Baidu BCE imports and demo `main1` from `FileTask`**

Delete imports under `com.baidubce.*`. Delete the `main1` method that creates BCE STS and BOS clients. Keep existing `BosFileServiceImpl.writeBos(fileName)` calls so export completion still records the local file name as the file key.

- [x] **Step 3: Delete unused BOS helper classes**

Delete `BosUtils.java` and `BosFileClientTuple.java` after confirming only deleted or rewritten code references them.

- [x] **Step 4: Verify BOS references are gone**

Run:

```bash
rg -n 'com\\.baidubce|BosClient|BosUtils|BosFileClientTuple|StsClient|GetSessionToken' apps/da/src/main/java
```

Expected: no output except class/interface names intentionally retained as product API names such as `BosFileService`.

### Task 3: Remove Monitoring And SkyWalking Runtime Hooks

**Files:**
- Delete: `apps/da/src/main/java/com/graphinsight/indicator/configuration/MonitoringConfiguration.java`
- Modify: `apps/da/src/main/java/com/graphinsight/indicator/aop/OperateLogAspect.java`
- Modify: `apps/da/src/main/java/com/graphinsight/indicator/interceptor/MybatisplusOperateInterceptor.java`
- Modify: `apps/da/src/main/resources/logback.xml`

**Interfaces:**
- Consumes: audit log entities with `traceId` string field.
- Produces: audit records with a local generated trace id and standard Logback layouts.

- [x] **Step 1: Delete `MonitoringConfiguration`**

Remove the JavaMelody servlet/filter registration class.

- [x] **Step 2: Replace SkyWalking trace id calls**

In both audit classes, remove `org.apache.skywalking.apm.toolkit.trace.TraceContext` imports and replace `TraceContext.traceId()` with:

```java
java.util.UUID.randomUUID().toString()
```

- [x] **Step 3: Convert Logback layouts**

Replace every `org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdPatternLogbackLayout` layout with `ch.qos.logback.classic.PatternLayout`, and replace `%tid` in patterns with a normal field such as `%X{traceId:-}`.

- [x] **Step 4: Verify removed monitoring references are gone**

Run:

```bash
rg -n 'javamelody|skywalking|TraceContext|TraceIdPatternLogbackLayout|%tid|micrometer|actuator' apps/da/src/main apps/da/pom.xml
```

Expected: no output.

### Task 4: Remove Swagger Runtime Coupling

**Files:**
- Delete: `apps/da/src/main/java/com/graphinsight/indicator/configuration/SwaggerConfiguration.java`
- Modify: Java files under `apps/da/src/main/java`

**Interfaces:**
- Consumes: existing controllers, DTOs, VOs, and entities.
- Produces: the same compiled Java classes without Swagger/Springfox annotations.

- [x] **Step 1: Delete Swagger configuration**

Delete `SwaggerConfiguration.java`.

- [x] **Step 2: Mechanically remove Swagger imports and annotations**

For Java files under `apps/da/src/main/java`, remove imports matching:

```java
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import springfox.documentation.annotations.ApiIgnore;
```

Remove annotation lines beginning with `@Api`, `@ApiModel`, `@ApiModelProperty`, `@ApiOperation`, and `@ApiIgnore`.

- [x] **Step 3: Verify Swagger references are gone**

Run:

```bash
rg -n 'io\\.swagger|springfox|@Api|@ApiModel|@ApiModelProperty|@ApiOperation|@ApiIgnore' apps/da/src/main/java apps/da/pom.xml
```

Expected: no output except unrelated words inside comments.

### Task 5: Compile, Measure, Restart, And Smoke Test

**Files:**
- Modify as needed: compile-error files surfaced by Maven only if errors are caused by removed dependencies.

**Interfaces:**
- Consumes: all earlier tasks.
- Produces: a compiling DA JAR, measured dependency reduction, restarted local DA service, and smoke-test results for core demo paths.

- [x] **Step 1: Compile DA**

Run:

```bash
cd apps/da
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_321.jdk/Contents/Home \
PATH="/Library/Java/JavaVirtualMachines/jdk1.8.0_321.jdk/Contents/Home/bin:$PATH" \
mvn -DskipTests package
```

Expected: build success. If compile fails on imports from removed dependencies, remove the imports or replace the narrow behavior according to Tasks 2-4.

- [x] **Step 2: Measure package result**

Run:

```bash
ls -lh target/da-indicator-0.0.1-SNAPSHOT.jar
jar tf target/da-indicator-0.0.1-SNAPSHOT.jar | awk '/^BOOT-INF\/lib\// {print}' | wc -l
jar tf target/da-indicator-0.0.1-SNAPSHOT.jar | rg 'hadoop|hbase|selenium|cssbox|zookeeper|bce-java-sdk|javamelody|springfox|skywalking|hanlp-restful|protobuf|h2' || true
```

Expected: JAR size and library count are lower than the baseline 229 MB and 332 libs, and removed dependency families are absent.

- [x] **Step 3: Restart DA and run smoke tests**

Run:

```bash
./scripts/insightmind.sh restart da
./scripts/insightmind.sh status
curl -s http://localhost:8091/api/graph/reasoning/measure/MEAS_workforce_headcount/compatible-dimensions
curl -s -X POST http://localhost:8091/bi/v1/datasource/query -H 'Content-Type: application/json' -d '{"configureList":[{"code":"MEAS_workforce_headcount"}],"filterList":[],"pageSize":1000,"pageNum":1}'
curl -s -X POST http://localhost:8091/bi/v1/dimension/value/list -H 'Content-Type: application/json' -d '{"pageNo":1,"pageSize":20,"code":"DIM_department","filterList":[]}'
```

Expected: DA is running and core demo endpoints respond with JSON rather than dependency-related startup errors.
