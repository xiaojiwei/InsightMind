# DA Mainline Dependency Pruning Design

## Context

InsightMind DA currently packages a large Spring Boot executable JAR for the mainline product. The current `apps/da/target/da-indicator-0.0.1-SNAPSHOT.jar` is about 229 MB and contains 332 runtime libraries under `BOOT-INF/lib`.

Stakeholder feedback is that drill-down and import-related delivery brings too many JARs and installation dependencies. The desired change is permanent mainline cleanup, not only a demo profile. Common Java dependencies used by the product should remain.

The first cleanup round should preserve the current dashboard, graph reasoning, SQL generation, datasource query, dimension value, and drill-down flows.

## Goals

- Permanently remove clearly heavy, non-core, non-drill-down dependencies from the mainline DA build.
- Remove or disable code paths that require those dependencies so the mainline build stays honest.
- Preserve common runtime dependencies and product-critical query paths.
- Keep the change reviewable by limiting the first round to low-risk removals.
- Measure the effect through JAR size and runtime library count before and after.

## Non-Goals

- Do not introduce a demo-only Maven profile as the main solution.
- Do not remove Spring Boot, Spring Web, JDBC, MyBatis/MyBatis-Plus, Druid, dynamic-datasource, MySQL driver, Jackson/Fastjson, Lombok, Guava, Hutool, Jena, ANTLR, validation, or test infrastructure in the first round.
- Do not remove POI or JPA/Hibernate in the first round. They are broader business capability dependencies and need separate decisions.
- Do not rewrite the dashboard, AD web app, or graph reasoning architecture.
- Do not change database schemas or credentials.

## First-Round Removal Scope

Remove these dependency families from `apps/da/pom.xml` and the mainline runtime package:

- BOS/file-cloud stack: `com.baidubce:bce-java-sdk` and code that depends on BOS upload/download helpers. This is the largest cleanup target because it brings HBase, Hadoop, Netty, Jersey, and older Jackson transitive dependencies.
- Browser/rendering stack: `org.seleniumhq.selenium:selenium-java` and `net.sf.cssbox:cssbox`.
- Coordination and unused runtime libraries: `org.apache.zookeeper:zookeeper`, `com.google.protobuf:protobuf-java`, `com.h2database:h2`, `com.hankcs.hanlp.restful:hanlp-restful`.
- Built-in monitoring stack not required by the core query product: `io.micrometer:micrometer-registry-prometheus`, `spring-boot-starter-actuator`, `net.bull.javamelody:javamelody-core`, SkyWalking toolkit dependencies.
- Swagger UI/runtime generation stack if no longer required in packaged mainline: `io.springfox:springfox-swagger2`, `io.springfox:springfox-swagger-ui`, and the Swagger configuration class. Existing API annotation cleanup can be handled mechanically where needed for compilation.

Keep for this round:

- `org.apache.poi:poi-ooxml` and `net.sourceforge.javacsv:javacsv` until the team explicitly decides whether Excel import/export should be removed or replaced by CSV-only behavior.
- `spring-boot-starter-data-jpa` and `hibernate-core` until old DAO/model usage is separately retired.
- `org.ansj:ansj_seg`, `com.hankcs:hanlp`, `com.ibm.icu:icu4j`, and `org.apache.mahout:*` until keyword search and similarity features are evaluated as product decisions.
- `org.apache.calcite:calcite-core` because `PivotServiceImpl` and `MemorySchema` use it.

## Code Changes

The implementation should proceed in small commits or at least small patch groups:

1. Remove monitoring and Swagger runtime classes.
   - Delete or neutralize `MonitoringConfiguration`.
   - Delete or neutralize `SwaggerConfiguration`.
   - Remove SkyWalking-specific logback layout references and use standard Logback patterns.
   - Remove unused imports such as `net.bull.javamelody.internal.*`.

2. Remove BOS cloud file code from mainline.
   - Delete or isolate `BosFileServiceImpl`, `BosUtils`, and BOS-specific model helpers if they are only used by file export.
   - Change file export/downloading code that currently calls BOS to either return a clear unsupported response or use existing local-file behavior if already available.
   - Preserve datasource query and drill-down responses.

3. Remove browser/rendering and unused dependency declarations.
   - Remove Selenium, CSSBox, Zookeeper, Protobuf, H2, and HanLP restful dependencies.
   - Remove any stale imports or dead configuration that become compile errors.

4. Remove Swagger annotations only where required for compilation.
   - Prefer mechanical removal of `@Api`, `@ApiOperation`, `@ApiModel`, `@ApiModelProperty`, and related imports.
   - Do not alter endpoint paths or request/response models while removing documentation annotations.

## Expected Impact

The first round should remove a large share of transitive runtime libraries, especially from `bce-java-sdk`. The expected JAR reduction should be visible in:

- total executable JAR size,
- `BOOT-INF/lib` count,
- removal of HBase/Hadoop/large Netty families unless another retained dependency brings them back.

Exact size targets should be recorded after implementation because Maven transitive resolution may keep some shared libraries through retained dependencies.

## Risks

- Removing Swagger annotations can create many mechanical compile errors across controllers and DTOs. The implementation should use search-driven cleanup and compile after each group.
- BOS removal may break explicit file download/export endpoints. This is acceptable only if those endpoints are outside the first-round product boundary or return a clear unsupported message.
- Removing monitoring dependencies changes operational observability. Standard logs should remain available.
- Existing dirty working tree changes must not be reverted or mixed into this cleanup.

## Validation

Run these checks after implementation:

```bash
cd apps/da
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_321.jdk/Contents/Home \
PATH="/Library/Java/JavaVirtualMachines/jdk1.8.0_321.jdk/Contents/Home/bin:$PATH" \
mvn -DskipTests package
```

Then measure:

```bash
ls -lh target/da-indicator-0.0.1-SNAPSHOT.jar
jar tf target/da-indicator-0.0.1-SNAPSHOT.jar | awk '/^BOOT-INF\/lib\// {print}' | wc -l
jar tf target/da-indicator-0.0.1-SNAPSHOT.jar | rg 'hadoop|hbase|selenium|cssbox|zookeeper|bce-java-sdk|javamelody|springfox|skywalking|hanlp-restful|protobuf|h2' || true
```

Restart and smoke test:

```bash
./scripts/insightmind.sh restart da
./scripts/insightmind.sh status
```

Core HTTP checks:

```bash
curl -s http://localhost:8091/api/graph/reasoning/measure/MEAS_workforce_headcount/compatible-dimensions
curl -s -X POST http://localhost:8091/bi/v1/datasource/query \
  -H 'Content-Type: application/json' \
  -d '{"configureList":[{"code":"MEAS_workforce_headcount"}],"filterList":[],"pageSize":1000,"pageNum":1}'
curl -s -X POST http://localhost:8091/bi/v1/dimension/value/list \
  -H 'Content-Type: application/json' \
  -d '{"pageNo":1,"pageSize":20,"code":"DIM_department","filterList":[]}'
```

AD integration smoke checks:

```bash
curl -s -X POST http://localhost:8080/api/ad/v1/load \
  -H 'Content-Type: application/json' \
  -d '{"measures":["ad.workforce_headcount"],"dimensions":["ad.department"],"filters":[],"order":{"ad.workforce_headcount":"desc"},"limit":30}'
curl -s http://localhost:8080/api/business-kg/stats
```

## Approval Point

Implementation should start only after this design is reviewed and approved.
