# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

- **Build**: `mvn package -DskipTests` (tests are skipped by default in surefire config)
- **Run tests**: `mvn test -DskipTests=false` (overrides the default skip)
- **Run single test**: `mvn test -DskipTests=false -Dtest=ClassName`
- **Run the app**: `java -jar target/da-indicator-0.0.1-SNAPSHOT.jar` or `mvn spring-boot:run`
- **Active profile**: `dev` by default (set in `application.yml`); other profiles: `test`, `prod`, `debug`, `develop`, `ontest`
- **Code generation**: `MybatisGenerator.java` in the root package generates MyBatis-Plus CRUD code (entities, mappers, services, controllers) for the `auto/` package

## Tech Stack

- Spring Boot 2.5.6, Java 8, Maven
- MyBatis-Plus 3.4.1 (ORM) with dynamic-datasource for multi-DB routing (MySQL + Doris)
- Druid connection pool, Redis (Jedis + Spring Data Redis)
- ANTLR 4.11.1 for custom expression language parsing
- Apache Jena 3.17.0 for RDF/OWL knowledge graph
- Apache Ignite 2.14.0 for distributed computing
- Quartz scheduling, Zookeeper 3.4.10
- HanLP + Ansj for Chinese NLP/word segmentation
- Swagger 2 (springfox) for API docs
- Prometheus metrics (micrometer), SkyWalking APM, JavaMelody monitoring

## Architecture

The codebase follows a layered architecture within `com.graphinsight.indicator`:

**Web layer** — Controllers are split across multiple subpackages:
- `controller/` — main REST controllers and DataGPT controllers
- `api/` — external API entry points (`IndicatorController`)
- `openapi/` — third-party open API
- `auto/controller/` — auto-generated CRUD controllers (MyBatis-Plus codegen)
- `requirement/` — user requirement/wish controllers

**Service layer** — Interfaces in `service/`, implementations in `service/impl/`. Key services: `BuildSqlService` (metric SQL generation), `DataQueryService`, `DimensionQueryService`, `PivotService`, `ChartQueryService`, plus AI/GPT-related services and NLP word services. Doris-specific services in `doris/service/`.

**Manager layer** — `manager/` contains business logic orchestrators that sit above services. Key managers: `MeasureManager`, `DimensionManager`, `CategoryManager`, `SQLGenerateManager`, `DismantlingTreeManager`, `DecisionTreeManager`, `BloodManager` (lineage), `PortalManager`, `MeasureSimilarityManager`, `DorisQueryManager`.

**Data access** — Two patterns coexist:
- MyBatis-Plus auto-generated code in `auto/` (`controller/`, `entity/`, `mapper/`, `service/`) — full CRUD stack from codegen
- Hand-written DAOs in `dao/` for custom queries
- Doris-specific data layer in `doris/` (`entity/`, `mapper/`, `service/`) for the analytics DB
- Multi-datasource via `dynamic-datasource` (`@DS` annotation on mapper/service methods to route between MySQL and Doris)

**Custom expression language (LAX/XLax)** — ANTLR 4.11.1-based DSLs. Grammar files (`.g4`) are the source of truth; generated lexers/parsers live alongside them. Key dialects:
- `lax/filter/` — Expression filtering with function evaluation (`LaxExpr.g4`)
- `lax/measopt/` — Measure optimization expression parsing (`Var.g4`)
- `lax/var/` — Variable expression language (`Var.g4`)
- `lax/ifelse/` — If-else expression evaluation (`IFElse.g4`, `MU.g4`)
- `lax/expression/` — Reusable expression nodes (`Expression.g4`)
- `xlax/` — Extended expression language variants: `exp/`, `lod/`, `xlod/`, `labeledexpr/`, `html/`

**Knowledge graph** — `graph/`, backed by `src/main/resources/indicator-data.ttl`:
- `GraphStore` — Loads the Turtle (.ttl) file into a Jena in-memory RDF model, with hot-reload on file change
- `IndicatorOntology` — OWL ontology constants (Measure, Dimension, DwTable classes and their properties)
- `GraphIndicatorServiceImpl` — SPARQL-based indicator queries against the RDF graph

**AOP cross-cutting** — `aop/`: aspects driven by custom annotations from `annotation/`:
- `AuthCheckAspect` — method-level auth via `@AuthCheck`
- `CacheAspect` — caching layer with `@CheckCacheVersion` / `@ReloadCache`
- `OperateLogAspect` — operation logging via `@OperateLog`
- `MeasureMonitorAspect` — metric monitoring instrumentation

**Configuration** — `configuration/`: Spring `@Configuration` classes including `MybatisPlusConfiguration`, `RedisConfiguration`, `DruidConfiguration`, `SwaggerConfiguration`, `WebMvcConfiguration`, `ScheduleConfiguration`, thread pool configs, and REST client configs.

**Authentication & request pipeline**: JWT-based (jjwt + nimbus-jose-jwt), IDaaS OAuth2 integration, COA login manager. HTTP layer: `filter/`, `interceptor/` (`AuthenticationInterceptor`, `CurrentUserMethodArgumentResolver`, `MybatisplusOperateInterceptor`), `handler/`.

**Job scheduling**: `job/`, `schedule/`, `listener/` — Quartz-based distributed scheduled tasks with Zookeeper coordination. `init/DimensionAnalysisTaskExecute` runs at startup.

**Test structure**: Tests under `src/test/java/com/graphinsight/indicator/` are mostly in the root test package directly. Some feature-specific tests live in subdirectories (`measure/`, `dimension/`, `word/`, `similarity/`, `cache/`, `job/`). Test resources are configured in pom.xml to use `src/main/resources` (no separate test resource directory).

**Important**: Application profile YAML files (`application-*.yml`) contain hardcoded database credentials and OAuth2 client secrets. Do not commit credential changes or expose these values.
