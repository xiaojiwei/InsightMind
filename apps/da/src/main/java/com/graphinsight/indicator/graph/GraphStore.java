package com.graphinsight.indicator.graph;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring singleton that holds the Jena in-memory RDF Model for the
 * Indicator Platform knowledge graph.
 *
 * <p>The Turtle (.ttl) file is read from the filesystem path specified by
 * {@code indicator.graph.data-path}. Put any absolute or relative path there,
 * e.g.:
 * <pre>
 *   indicator.graph.data-path=/data/indicator/indicator-data.ttl
 * </pre>
 *
 * <p>每次调用 {@link #getModel()} 时自动检测文件修改时间，若发生变化则热加载，
 * 无需重启应用。
 */
@Slf4j
@Component
public class GraphStore {

    /** Filesystem path to the Turtle data file. */
    @Value("${indicator.graph.data-path:/Users/xiaojiwei/kd/output/business_kg/indicator-data.ttl}")
    private String dataPath;

    /**
     * Optional inferred triples path. When empty, GraphStore looks for
     * indicator-inferred.ttl next to indicator-data.ttl.
     */
    @Value("${indicator.graph.inferred-data-path:}")
    private String inferredDataPath;

    /** Volatile so reads always see the latest reload without locking. */
    private volatile Model model;

    /** 上次加载时的主图 + 推理图文件修改签名。 */
    private final AtomicLong lastModified = new AtomicLong(-1L);

    @PostConstruct
    public void init() {
        Path path = Paths.get(dataPath);
        log.info("[GraphStore] Loading knowledge graph from {}", path.toAbsolutePath());

        if (!Files.exists(path)) {
            log.warn("[GraphStore] File not found: {} — starting with empty graph", path.toAbsolutePath());
            this.model = ModelFactory.createDefaultModel();
            return;
        }

        Model fresh = ModelFactory.createDefaultModel();
        try {
            readTurtleInto(fresh, path);
            Path inferredPath = resolveInferredPath(path);
            if (Files.exists(inferredPath)) {
                readTurtleInto(fresh, inferredPath);
                log.info("[GraphStore] Loaded inferred triples from {}", inferredPath.toAbsolutePath());
            }
            this.model = fresh;
            lastModified.set(modifiedSignature(path, inferredPath));
            log.info("[GraphStore] Loaded {} triples from {}", fresh.size(), path.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[GraphStore] Failed to load {}: {} — starting with empty graph",
                    path.toAbsolutePath(), e.getMessage());
            this.model = ModelFactory.createDefaultModel();
        }
    }

    /**
     * 返回当前 Jena Model。
     * 每次调用时检测 TTL 文件是否被修改，若修改则自动热加载。
     */
    public Model getModel() {
        checkAndReloadIfChanged();
        return model;
    }

    /**
     * Hot-reload the graph from the same file path.
     * Thread-safe: builds a new Model first, then swaps the reference atomically.
     */
    public synchronized void reload() {
        log.info("[GraphStore] Reloading knowledge graph …");
        init();
    }

    /**
     * 检测文件修改时间，若已变化则触发热加载。
     * 非阻塞快速检查，只有真正变化时才进入 synchronized reload。
     */
    private void checkAndReloadIfChanged() {
        Path path = Paths.get(dataPath);
        if (!Files.exists(path)) {
            return;
        }
        long current = modifiedSignature(path, resolveInferredPath(path));
        if (current != lastModified.get()) {
            reload();
        }
    }

    private void readTurtleInto(Model target, Path path) throws Exception {
        try (InputStream in = new FileInputStream(path.toFile())) {
            target.read(in, null, "TURTLE");
        }
    }

    private Path resolveInferredPath(Path dataFile) {
        String configured = inferredDataPath == null ? "" : inferredDataPath.trim();
        if (!configured.isEmpty()) {
            return Paths.get(configured);
        }
        Path parent = dataFile.getParent();
        if (parent == null) {
            return Paths.get("indicator-inferred.ttl");
        }
        return parent.resolve("indicator-inferred.ttl");
    }

    private long modifiedSignature(Path dataFile, Path inferredFile) {
        long dataModified = Files.exists(dataFile) ? dataFile.toFile().lastModified() : 0L;
        long inferredModified = Files.exists(inferredFile) ? inferredFile.toFile().lastModified() : 0L;
        return dataModified * 31L + inferredModified;
    }
}
