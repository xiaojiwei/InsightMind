package com.graphinsight.indicator.graph;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.enums.SourceType;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.model.DataConnection;
import com.graphinsight.indicator.model.DimMeasTableColumn;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.IndicatorTuple;
import com.graphinsight.indicator.model.Level;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.OperationItem;
import com.graphinsight.indicator.model.Table;
import com.graphinsight.indicator.model.dto.AuthDimensionBloodCheckResult;
import com.graphinsight.indicator.model.dto.BaseInfoDTO;
import com.graphinsight.indicator.model.dto.CategoryDTO;
import com.graphinsight.indicator.model.dto.DimensionHistogramRequest;
import com.graphinsight.indicator.model.dto.HistogramInfo;
import com.graphinsight.indicator.service.IndicatorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link IndicatorService} implementation backed by an Apache Jena in-memory
 * RDF/OWL knowledge graph loaded from a Turtle (.ttl) file.
 *
 * <p>This bean is registered as {@code "graphIndicatorService"} and is
 * <em>not</em> marked {@code @Primary}, so the existing MySQL-backed
 * {@code IndicatorServiceImpl} remains the default. Inject this bean
 * explicitly by name when you want the graph-based implementation.
 *
 * <p>Ontology namespace constants are in {@link IndicatorOntology}.
 * The data file path is configured via {@code indicator.graph.data-path}
 * (default: {@code indicator-data.ttl} on the classpath).
 */
@Slf4j
@Primary
@Service("graphIndicatorService")
public class GraphIndicatorServiceImpl implements IndicatorService {

    private static final String P = IndicatorOntology.PREFIXES;

    @Autowired
    private GraphStore graphStore;

    // =========================================================================
    // IndicatorService — simple query methods
    // =========================================================================

    @Override
    public Boolean belongToCategory(String measureCode, String categoryCode) {
        // Walk the category parent chain transitively (ind:categoryParent*)
        String sparql = P +
                "ASK {\n" +
                "  ?meas a ind:Measure ; ind:code ?measCode ;\n" +
                "        ind:belongsToCategory ?leaf .\n" +
                "  ?leaf (ind:categoryParent*) ?cat .\n" +
                "  ?cat  ind:code ?catCode .\n" +
                "  FILTER(?measCode = \"" + esc(measureCode) + "\")\n" +
                "  FILTER(?catCode  = \"" + esc(categoryCode) + "\")\n" +
                "}\n";
        return execAsk(sparql);
    }

    @Override
    public BaseInfoDTO getByCode(String code) {
        // Try Measure first, then Dimension
        String sparql = P +
                "SELECT ?cnName ?enName ?viewTypeCode WHERE {\n" +
                "  { ?e a ind:Measure    ; ind:code \"" + esc(code) + "\" .\n" +
                "    OPTIONAL { ?e ind:cnName ?cnName }\n" +
                "    OPTIONAL { ?e ind:enName ?enName }\n" +
                "    OPTIONAL { ?e ind:viewTypeCode ?viewTypeCode }\n" +
                "  } UNION {\n" +
                "    ?e a ind:Dimension  ; ind:code \"" + esc(code) + "\" .\n" +
                "    OPTIONAL { ?e ind:cnName ?cnName }\n" +
                "    OPTIONAL { ?e ind:enName ?enName }\n" +
                "    OPTIONAL { ?e ind:viewTypeCode ?viewTypeCode }\n" +
                "  }\n" +
                "} LIMIT 1\n";
        List<QuerySolution> rows = execSelect(sparql);
        if (rows.isEmpty()) return null;
        QuerySolution r = rows.get(0);
        BaseInfoDTO dto = new BaseInfoDTO();
        dto.setCode(code);
        dto.setCnName(str(r, "cnName"));
        dto.setEnName(str(r, "enName"));
        dto.setViewType(intVal(r, "viewTypeCode"));
        return dto;
    }

    @Override
    public CategoryDTO getCategoryById(Long id) {
        String sparql = P +
                "SELECT ?name WHERE {\n" +
                "  ?cat a ind:Category ; ind:id " + id + " ;\n" +
                "       ind:name ?name .\n" +
                "} LIMIT 1\n";
        List<QuerySolution> rows = execSelect(sparql);
        if (rows.isEmpty()) return null;
        CategoryDTO dto = new CategoryDTO();
        dto.setId(id);
        dto.setName(str(rows.get(0), "name"));
        return dto;
    }

    @Override
    public List<HistogramInfo> listTableHistogram(Set<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) return Collections.emptyList();
        String sparql = P +
                "SELECT ?tableName ?tableRowNum ?maxScanNum WHERE {\n" +
                "  ?tbl a ind:DwTable ; ind:tableName ?tableName ;\n" +
                "       ind:hasTableHistogram ?hist .\n" +
                "  ?hist ind:tableRowNum ?tableRowNum .\n" +
                "  OPTIONAL { ?hist ind:maxScanNum ?maxScanNum }\n" +
                values("tableName", tableNames) + "\n" +
                "}\n";
        List<QuerySolution> rows = execSelect(sparql);
        List<HistogramInfo> result = new ArrayList<>();
        for (QuerySolution r : rows) {
            HistogramInfo h = new HistogramInfo();
            h.setTableName(str(r, "tableName"));
            h.setTableRowNum(longVal(r, "tableRowNum"));
            h.setMaxScanNum(longVal(r, "maxScanNum"));
            result.add(h);
        }
        return result;
    }

    @Override
    public List<HistogramInfo> listDimensionHistogram(List<DimensionHistogramRequest> requests) {
        if (requests == null || requests.isEmpty()) return Collections.emptyList();
        List<HistogramInfo> result = new ArrayList<>();
        for (DimensionHistogramRequest req : requests) {
            String dimCode = req.getCode();
            Set<String> tblNames = req.getTableNames();
            String sparql = P +
                    "SELECT ?tableName ?dimRowNum ?tableRowNum WHERE {\n" +
                    "  ?hist a ind:DimHistogram ;\n" +
                    "        ind:histDimCode   \"" + esc(dimCode) + "\" ;\n" +
                    "        ind:histTableName ?tableName ;\n" +
                    "        ind:dimensionRowNum ?dimRowNum .\n" +
                    "  OPTIONAL { ?hist ind:tableRowNum ?tableRowNum }\n" +
                    (tblNames != null && !tblNames.isEmpty()
                            ? values("tableName", tblNames) + "\n" : "") +
                    "}\n";
            List<QuerySolution> rows = execSelect(sparql);
            for (QuerySolution r : rows) {
                HistogramInfo h = new HistogramInfo();
                h.setDimCode(dimCode);
                h.setTableName(str(r, "tableName"));
                h.setDimensionRowNum(longVal(r, "dimRowNum"));
                Long tblRowNum = longVal(r, "tableRowNum");
                h.setTableRowNum(tblRowNum);
                if (tblRowNum != null && tblRowNum > 0) {
                    long dimRowNum = h.getDimensionRowNum() != null ? h.getDimensionRowNum() : 0L;
                    h.setDispersionDegree(BigDecimal.valueOf(dimRowNum)
                            .divide(BigDecimal.valueOf(tblRowNum), 6, RoundingMode.HALF_UP));
                }
                result.add(h);
            }
        }
        return result;
    }

    // =========================================================================
    // IndicatorService — listing methods
    // =========================================================================

    @Override
    public List<Measure> listAllMeasure() {
        List<QuerySolution> rows = execSelect(P +
                "SELECT DISTINCT ?code ?cnName ?enName ?measTypeCode ?definition ?description\n" +
                "WHERE {\n" +
                "  ?meas a ind:Measure ; ind:code ?code ; ind:cnName ?cnName .\n" +
                "  OPTIONAL { ?meas ind:enName ?enName }\n" +
                "  OPTIONAL { ?meas ind:measTypeCode ?measTypeCode }\n" +
                "  OPTIONAL { ?meas ind:definition ?definition }\n" +
                "  OPTIONAL { ?meas ind:description ?description }\n" +
                "} ORDER BY ?code\n");
        return buildMeasureBaseList(rows);
    }

    @Override
    public List<Measure> listMeasureByName(String cnName) {
        if (!StringUtils.hasText(cnName)) return listAllMeasure();
        List<QuerySolution> rows = execSelect(P +
                "SELECT DISTINCT ?code ?cnName ?enName ?measTypeCode ?definition ?description\n" +
                "WHERE {\n" +
                "  ?meas a ind:Measure ; ind:code ?code ; ind:cnName ?cnName .\n" +
                "  OPTIONAL { ?meas ind:enName ?enName }\n" +
                "  OPTIONAL { ?meas ind:measTypeCode ?measTypeCode }\n" +
                "  OPTIONAL { ?meas ind:definition ?definition }\n" +
                "  OPTIONAL { ?meas ind:description ?description }\n" +
                "  FILTER(CONTAINS(LCASE(str(?cnName)), LCASE(\"" + esc(cnName) + "\")))\n" +
                "} ORDER BY ?code\n");
        return buildMeasureBaseList(rows);
    }

    @Override
    public List<Dimension> listAllDimension() {
        List<QuerySolution> rows = execSelect(P +
                "SELECT DISTINCT ?code ?cnName ?enName ?dimTypeCode ?viewTypeCode ?definition ?description\n" +
                "WHERE {\n" +
                "  ?dim a ind:Dimension ; ind:code ?code ; ind:cnName ?cnName .\n" +
                "  OPTIONAL { ?dim ind:enName ?enName }\n" +
                "  OPTIONAL { ?dim ind:dimTypeCode ?dimTypeCode }\n" +
                "  OPTIONAL { ?dim ind:viewTypeCode ?viewTypeCode }\n" +
                "  OPTIONAL { ?dim ind:definition ?definition }\n" +
                "  OPTIONAL { ?dim ind:description ?description }\n" +
                "} ORDER BY ?code\n");
        return buildDimensionBaseList(rows);
    }

    @Override
    public List<Dimension> listDegenerateDimension() {
        // dimTypeCode 0 = DEGENERATE_DIM
        List<QuerySolution> rows = execSelect(P +
                "SELECT DISTINCT ?code ?cnName ?enName ?dimTypeCode ?viewTypeCode ?definition ?description\n" +
                "WHERE {\n" +
                "  ?dim a ind:Dimension ; ind:code ?code ; ind:cnName ?cnName ;\n" +
                "       ind:dimTypeCode 0 .\n" +
                "  OPTIONAL { ?dim ind:enName ?enName }\n" +
                "  OPTIONAL { ?dim ind:viewTypeCode ?viewTypeCode }\n" +
                "  OPTIONAL { ?dim ind:definition ?definition }\n" +
                "  OPTIONAL { ?dim ind:description ?description }\n" +
                "  BIND(0 AS ?dimTypeCode)\n" +
                "} ORDER BY ?code\n");
        return buildDimensionBaseList(rows);
    }

    @Override
    public List<BaseInfoDTO> listDateDimension(Set<String> dimensionCodes, Set<String> measureCodes) {
        // Date dimensions: viewTypeCode 1..6 (DAY, WEEK, MONTH, SEASON, YEAR, HOUR)
        // We return dimensions that (a) are date-typed and (b) have a relation to the given measures
        // (share a fact table). For simplicity we filter on viewTypeCode and the code set.
        String codeFilter = (dimensionCodes != null && !dimensionCodes.isEmpty())
                ? values("code", dimensionCodes) + "\n"
                : "";
        String sparql = P +
                "SELECT DISTINCT ?code ?cnName ?enName ?viewTypeCode WHERE {\n" +
                "  ?dim a ind:Dimension ; ind:code ?code ; ind:cnName ?cnName ;\n" +
                "       ind:viewTypeCode ?viewTypeCode .\n" +
                "  OPTIONAL { ?dim ind:enName ?enName }\n" +
                "  FILTER(?viewTypeCode >= 1 && ?viewTypeCode <= 6)\n" +
                codeFilter +
                "} ORDER BY ?code\n";
        List<QuerySolution> rows = execSelect(sparql);
        List<BaseInfoDTO> result = new ArrayList<>();
        for (QuerySolution r : rows) {
            BaseInfoDTO dto = new BaseInfoDTO();
            dto.setCode(str(r, "code"));
            dto.setCnName(str(r, "cnName"));
            dto.setEnName(str(r, "enName"));
            dto.setViewType(intVal(r, "viewTypeCode"));
            result.add(dto);
        }
        return result;
    }

    // =========================================================================
    // IndicatorService — hasRelation
    // =========================================================================

    @Override
    public Boolean hasRelation(Set<String> dimensionCodeList, Set<String> measureCodeList) {
        if (dimensionCodeList == null || dimensionCodeList.isEmpty()
                || measureCodeList == null || measureCodeList.isEmpty()) {
            return false;
        }
        // True if ANY measure (or its dependency chain) shares a fact table with ANY dimension
        String sparql = P +
                "ASK {\n" +
                "  ?meas a ind:Measure ; ind:code ?measCode ; ind:hasMeasureApp ?app .\n" +
                "  {\n" +
                "    ?app ind:appliesToTable ?sharedTable .\n" +
                "  } UNION {\n" +
                "    ?app ind:dependsOnMeasApp+ ?childApp .\n" +
                "    ?childApp ind:appliesToTable ?sharedTable .\n" +
                "  }\n" +
                "  ?dim a ind:Dimension ; ind:code ?dimCode ; ind:hasDimApp ?dimApp .\n" +
                "  ?dimApp ind:dimFactTable ?sharedTable .\n" +
                values("measCode", measureCodeList) + "\n" +
                values("dimCode",  dimensionCodeList) + "\n" +
                "}\n";
        return execAsk(sparql);
    }

    // =========================================================================
    // IndicatorService — getIndicatorTableInfo
    // =========================================================================

    @Override
    public IndicatorTuple getIndicatorTableInfo(Set<String> dimensionCodeList,
                                                Set<String> measureCodeList) {
        return getIndicatorTableInfo(dimensionCodeList, measureCodeList, false);
    }

    @Override
    public IndicatorTuple getIndicatorTableInfo(Set<String> dimensionCodeList,
                                                Set<String> measureCodeList,
                                                boolean isDetail) {
        IndicatorTuple tuple = new IndicatorTuple();
        Set<Measure> measures = new LinkedHashSet<>();
        Set<Dimension> dimensions = new LinkedHashSet<>();

        if (measureCodeList != null && !measureCodeList.isEmpty()) {
            measures.addAll(buildMeasures(measureCodeList));
        }
        if (dimensionCodeList != null && !dimensionCodeList.isEmpty()) {
            dimensions.addAll(buildDimensions(dimensionCodeList));
        }

        // 公共维度映射：将逻辑公共维度（如"日期"）映射到各指标事实表的物理列
        if (!measures.isEmpty() && !dimensions.isEmpty()) {
            buildNaturalDimMappings(measures, dimensions);
        }

        tuple.setMeasureSet(measures);
        tuple.setDimensionSet(dimensions);
        return tuple;
    }

    @Override
    public Dimension getDimensionTableInfo(String dimCode) {
        if (dimCode == null) return null;
        List<Dimension> dims = buildDimensions(Collections.singleton(dimCode));
        return dims.isEmpty() ? null : dims.get(0);
    }

    // =========================================================================
    // IndicatorService — checkBloodByAuthDimension
    // =========================================================================

    @Override
    public List<AuthDimensionBloodCheckResult> checkBloodByAuthDimension(
            Set<String> authDimensionCodes,
            Set<String> dimensionCodes,
            Set<String> measureCodes) {

        List<AuthDimensionBloodCheckResult> results = new ArrayList<>();
        if (authDimensionCodes == null) return results;

        for (String authDimCode : authDimensionCodes) {
            AuthDimensionBloodCheckResult r = new AuthDimensionBloodCheckResult();
            r.setAuthDimensionCode(authDimCode);
            r.setDimensionCodes(dimensionCodes);
            r.setMeasureCodes(measureCodes);

            // Check if this auth dim shares a fact table with any of the requested
            // measures (directly or via their dependency chain).
            Set<String> allDimCodes = new HashSet<>();
            allDimCodes.add(authDimCode);
            if (dimensionCodes != null) allDimCodes.addAll(dimensionCodes);

            boolean hasBlood = hasRelation(allDimCodes,
                    measureCodes != null ? measureCodes : Collections.emptySet());
            r.setHasBlood(hasBlood);
            results.add(r);
        }
        return results;
    }

    // =========================================================================
    // Internal builders
    // =========================================================================

    /**
     * Build full {@link Measure} objects for the given set of codes.
     * <ol>
     *   <li>Fetch measure meta + applications (one SPARQL query).</li>
     *   <li>For DERIVED/EXTENDED apps, fetch transitive dependencies.</li>
     *   <li>Assemble Java objects.</li>
     * </ol>
     */
    private List<Measure> buildMeasures(Set<String> codes) {
        // Phase 1 — measure meta + their applications + fact table info
        String q1 = P +
                "SELECT DISTINCT ?measNode ?measCode ?measCnName ?measEnName\n" +
                "                ?measTypeCode ?definition ?description\n" +
                "                ?appNode ?applyTypeCode ?expression ?factColumn\n" +
                "                ?whereCondition ?hasColumnDT ?appAvailable\n" +
                "                ?schemaName ?tableName ?sourceTypeCode\n" +
                "                ?connDbType ?connHost ?connPort ?connUser ?connPassword ?connDbName\n" +
                "WHERE {\n" +
                "  ?measNode a ind:Measure ;\n" +
                "            ind:code ?measCode ;\n" +
                "            ind:cnName ?measCnName .\n" +
                "  OPTIONAL { ?measNode ind:enName ?measEnName }\n" +
                "  OPTIONAL { ?measNode ind:measTypeCode ?measTypeCode }\n" +
                "  OPTIONAL { ?measNode ind:definition ?definition }\n" +
                "  OPTIONAL { ?measNode ind:description ?description }\n" +
                "  OPTIONAL {\n" +
                "    ?measNode ind:hasMeasureApp ?appNode .\n" +
                "    ?appNode ind:applyTypeCode ?applyTypeCode .\n" +
                "    OPTIONAL { ?appNode ind:expression ?expression }\n" +
                "    OPTIONAL { ?appNode ind:factColumn ?factColumn }\n" +
                "    OPTIONAL { ?appNode ind:whereCondition ?whereCondition }\n" +
                "    OPTIONAL { ?appNode ind:hasColumnDT ?hasColumnDT }\n" +
                "    OPTIONAL { ?appNode ind:available ?appAvailable }\n" +
                "    OPTIONAL {\n" +
                "      ?appNode ind:appliesToTable ?tbl .\n" +
                "      ?tbl ind:schemaName ?schemaName ; ind:tableName ?tableName .\n" +
                "      OPTIONAL { ?tbl ind:sourceTypeCode ?sourceTypeCode }\n" +
                "      OPTIONAL {\n" +
                "        ?tbl ind:hasConnection ?conn .\n" +
                "        ?conn ind:dbType ?connDbType ; ind:host ?connHost ; ind:port ?connPort ;\n" +
                "              ind:dbUser ?connUser ; ind:dbPassword ?connPassword ; ind:dbName ?connDbName .\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                values("measCode", codes) + "\n" +
                "}\n";

        List<QuerySolution> appRows = execSelect(q1);

        // Collect non-ORIGIN app node URIs for dependency query
        Set<String> nonOriginAppUris = new HashSet<>();
        for (QuerySolution r : appRows) {
            Integer applyTypeCode = intVal(r, "applyTypeCode");
            RDFNode appNode = r.get("appNode");
            if (applyTypeCode != null && applyTypeCode != 0 && appNode != null) {
                nonOriginAppUris.add(appNode.asResource().getURI());
            }
        }

        // Phase 2 — transitive dependencies for DERIVED/EXTENDED apps
        Map<String, List<QuerySolution>> depsByTopApp = new LinkedHashMap<>();
        if (!nonOriginAppUris.isEmpty()) {
            String q2 = P +
                    "SELECT DISTINCT ?topApp ?depMeas ?depMeasCode ?depMeasCnName ?depMeasEnName\n" +
                    "                ?depMeasTypeCode ?depApp ?depApplyTypeCode ?depExpression\n" +
                    "                ?depFactColumn ?depWhereCondition ?depHasColumnDT\n" +
                    "                ?depSchemaName ?depTableName ?depSourceTypeCode\n" +
                    "                ?depConnDbType ?depConnHost ?depConnPort ?depConnUser ?depConnPassword ?depConnDbName\n" +
                    "WHERE {\n" +
                    "  ?topApp ind:dependsOnMeasApp+ ?depApp .\n" +
                    "  ?depMeas ind:hasMeasureApp ?depApp ;\n" +
                    "           ind:code ?depMeasCode ;\n" +
                    "           ind:cnName ?depMeasCnName .\n" +
                    "  OPTIONAL { ?depMeas ind:enName ?depMeasEnName }\n" +
                    "  OPTIONAL { ?depMeas ind:measTypeCode ?depMeasTypeCode }\n" +
                    "  ?depApp ind:applyTypeCode ?depApplyTypeCode .\n" +
                    "  OPTIONAL { ?depApp ind:expression ?depExpression }\n" +
                    "  OPTIONAL { ?depApp ind:factColumn ?depFactColumn }\n" +
                    "  OPTIONAL { ?depApp ind:whereCondition ?depWhereCondition }\n" +
                    "  OPTIONAL { ?depApp ind:hasColumnDT ?depHasColumnDT }\n" +
                    "  OPTIONAL {\n" +
                    "    ?depApp ind:appliesToTable ?depTbl .\n" +
                    "    ?depTbl ind:schemaName ?depSchemaName ; ind:tableName ?depTableName .\n" +
                    "    OPTIONAL { ?depTbl ind:sourceTypeCode ?depSourceTypeCode }\n" +
                    "    OPTIONAL {\n" +
                    "      ?depTbl ind:hasConnection ?depConn .\n" +
                    "      ?depConn ind:dbType ?depConnDbType ; ind:host ?depConnHost ; ind:port ?depConnPort ;\n" +
                    "               ind:dbUser ?depConnUser ; ind:dbPassword ?depConnPassword ; ind:dbName ?depConnDbName .\n" +
                    "    }\n" +
                    "  }\n" +
                    valuesUri("topApp", nonOriginAppUris) + "\n" +
                    "}\n";
            List<QuerySolution> depRows = execSelect(q2);
            for (QuerySolution r : depRows) {
                String topUri = r.getResource("topApp").getURI();
                depsByTopApp.computeIfAbsent(topUri, k -> new ArrayList<>()).add(r);
            }

            // Phase 2b — collect any non-origin dep app URIs discovered transitively
            // (e.g. MEAS_nss → MEAS_nss_numerator, where nss_numerator is itself derived)
            // and run a supplementary query so they also appear as keys in depsByTopApp.
            Set<String> transitiveNonOriginUris = new HashSet<>();
            for (List<QuerySolution> rows2 : depsByTopApp.values()) {
                for (QuerySolution r : rows2) {
                    Integer depATC = intVal(r, "depApplyTypeCode");
                    RDFNode depAppNode = r.get("depApp");
                    if (depATC != null && depATC != 0 && depAppNode != null) {
                        String uri = depAppNode.asResource().getURI();
                        if (!depsByTopApp.containsKey(uri)) {
                            transitiveNonOriginUris.add(uri);
                        }
                    }
                }
            }
            if (!transitiveNonOriginUris.isEmpty()) {
                String q2b = P +
                        "SELECT DISTINCT ?topApp ?depMeas ?depMeasCode ?depMeasCnName ?depMeasEnName\n" +
                        "                ?depMeasTypeCode ?depApp ?depApplyTypeCode ?depExpression\n" +
                        "                ?depFactColumn ?depWhereCondition ?depHasColumnDT\n" +
                        "                ?depSchemaName ?depTableName ?depSourceTypeCode\n" +
                        "                ?depConnDbType ?depConnHost ?depConnPort ?depConnUser ?depConnPassword ?depConnDbName\n" +
                        "WHERE {\n" +
                        "  ?topApp ind:dependsOnMeasApp+ ?depApp .\n" +
                        "  ?depMeas ind:hasMeasureApp ?depApp ;\n" +
                        "           ind:code ?depMeasCode ;\n" +
                        "           ind:cnName ?depMeasCnName .\n" +
                        "  OPTIONAL { ?depMeas ind:enName ?depMeasEnName }\n" +
                        "  OPTIONAL { ?depMeas ind:measTypeCode ?depMeasTypeCode }\n" +
                        "  ?depApp ind:applyTypeCode ?depApplyTypeCode .\n" +
                        "  OPTIONAL { ?depApp ind:expression ?depExpression }\n" +
                        "  OPTIONAL { ?depApp ind:factColumn ?depFactColumn }\n" +
                        "  OPTIONAL { ?depApp ind:whereCondition ?depWhereCondition }\n" +
                        "  OPTIONAL { ?depApp ind:hasColumnDT ?depHasColumnDT }\n" +
                        "  OPTIONAL {\n" +
                        "    ?depApp ind:appliesToTable ?depTbl .\n" +
                        "    ?depTbl ind:schemaName ?depSchemaName ; ind:tableName ?depTableName .\n" +
                        "    OPTIONAL { ?depTbl ind:sourceTypeCode ?depSourceTypeCode }\n" +
                        "    OPTIONAL {\n" +
                        "      ?depTbl ind:hasConnection ?depConn .\n" +
                        "      ?depConn ind:dbType ?depConnDbType ; ind:host ?depConnHost ; ind:port ?depConnPort ;\n" +
                        "               ind:dbUser ?depConnUser ; ind:dbPassword ?depConnPassword ; ind:dbName ?depConnDbName .\n" +
                        "    }\n" +
                        "  }\n" +
                        valuesUri("topApp", transitiveNonOriginUris) + "\n" +
                        "}\n";
                List<QuerySolution> extraDeps = execSelect(q2b);
                for (QuerySolution r : extraDeps) {
                    String topUri = r.getResource("topApp").getURI();
                    depsByTopApp.computeIfAbsent(topUri, k -> new ArrayList<>()).add(r);
                }
            }
        }

        // Assemble Measure objects grouped by code
        Map<String, List<QuerySolution>> byCode = appRows.stream()
                .collect(Collectors.groupingBy(r -> str(r, "measCode")));

        List<Measure> measures = new ArrayList<>();
        for (String code : codes) {
            List<QuerySolution> rows = byCode.getOrDefault(code, Collections.emptyList());
            if (rows.isEmpty()) {
                log.warn("[GraphIndicatorService] Measure not found in graph: {}", code);
                continue;
            }
            measures.add(assembleMeasure(code, rows, depsByTopApp));
        }
        return measures;
    }

    private Measure assembleMeasure(String code,
                                    List<QuerySolution> rows,
                                    Map<String, List<QuerySolution>> depsByTopApp) {
        Measure measure = new Measure();
        QuerySolution first = rows.get(0);
        measure.setCode(code);
        measure.setName(str(first, "measCnName"));
        measure.setAlias(str(first, "measEnName"));
        measure.setMeasType(MeasureType.getTypeByCode(intVal(first, "measTypeCode")));
        measure.setDefinition(str(first, "definition"));
        measure.setDescription(str(first, "description"));

        Set<String> processedApps = new HashSet<>();
        boolean allAppsUnavailable = true;

        for (QuerySolution row : rows) {
            RDFNode appNode = row.get("appNode");
            if (appNode == null) continue;
            String appUri = appNode.asResource().getURI();
            if (!processedApps.add(appUri)) continue;

            Integer available = intVal(row, "appAvailable");
            // available=0 表示下线；若 available 未设置则默认在线
            if (available == null || available != 0) {
                allAppsUnavailable = false;
            }

            Integer applyTypeCode = intVal(row, "applyTypeCode");
            Table table = new Table();
            table.setSchemaName(str(row, "schemaName"));
            table.setTableName(str(row, "tableName"));
            table.setSourceType(sourceType(intVal(row, "sourceTypeCode")));
            table.setFactColumn(str(row, "factColumn"));
            table.setWhereCondition(str(row, "whereCondition"));
            String expressionStr = str(row, "expression");
            table.setExpression(expressionStr);
            List<OperationItem> parsedExpList = parseExpressionToExpList(expressionStr, applyTypeCode);
            table.setExpList(parsedExpList);
            log.debug("[GraphStore] measure={} applyTypeCode={} expression={} expList={}",
                    code, applyTypeCode, expressionStr, parsedExpList);
            // For DERIVED/EXTENDED (applyTypeCode != 0): also set measure-level expression+expList,
            // which is checked by SqlCheckServiceImpl.checkMeasure() DERIVED/EXTENDED branches.
            if (applyTypeCode != null && applyTypeCode != 0) {
                if (measure.getExpression() == null) {
                    measure.setExpression(expressionStr);
                }
                if (CollectionUtils.isEmpty(measure.getExpList()) && parsedExpList != null) {
                    measure.setExpList(parsedExpList);
                }
            }
            MeasureType measType = MeasureType.getTypeByCode(applyTypeCode);
            table.setApplyType(measType);
            table.setMeasureType(measType);  // also set measureType so choiceMeasure can preserve it
            Boolean hasColDT = boolVal(row, "hasColumnDT");
            table.setHasColumnDT(hasColDT != null && hasColDT);
            table.setConnection(buildConnection(row, "connDbType", "connHost", "connPort",
                    "connUser", "connPassword", "connDbName"));

            // For DERIVED / EXTENDED: populate hasAllMeasureSet with child measures
            if (applyTypeCode != null && applyTypeCode != 0) {
                List<QuerySolution> deps = depsByTopApp.getOrDefault(appUri, Collections.emptyList());
                Map<String, Measure> childMap = new LinkedHashMap<>();
                for (QuerySolution dep : deps) {
                    String depCode = str(dep, "depMeasCode");
                    if (depCode == null) continue;
                    Measure child = childMap.computeIfAbsent(depCode, c -> {
                        Measure m = new Measure();
                        m.setCode(c);
                        m.setName(str(dep, "depMeasCnName"));
                        m.setAlias(str(dep, "depMeasEnName"));
                        m.setMeasType(MeasureType.getTypeByCode(intVal(dep, "depMeasTypeCode")));
                        return m;
                    });
                    // Add fact table entry for this child measure application
                    Table childTable = new Table();
                    childTable.setSchemaName(str(dep, "depSchemaName"));
                    childTable.setTableName(str(dep, "depTableName"));
                    childTable.setSourceType(sourceType(intVal(dep, "depSourceTypeCode")));
                    childTable.setFactColumn(str(dep, "depFactColumn"));
                    childTable.setWhereCondition(str(dep, "depWhereCondition"));
                    childTable.setExpression(str(dep, "depExpression"));
                    MeasureType childMeasType = MeasureType.getTypeByCode(intVal(dep, "depApplyTypeCode"));
                    childTable.setApplyType(childMeasType);
                    childTable.setMeasureType(childMeasType);
                    Boolean depHasDT = boolVal(dep, "depHasColumnDT");
                    childTable.setHasColumnDT(depHasDT != null && depHasDT);
                    childTable.setConnection(buildConnection(dep, "depConnDbType", "depConnHost", "depConnPort",
                            "depConnUser", "depConnPassword", "depConnDbName"));
                    child.getFactTable().add(childTable);

                    // If child is itself DERIVED/EXTENDED, also set expList and hasAllMeasureSet
                    // on both the child Measure AND its childTable.
                    // choiceMeasure() overwrites measure fields from table fields, so the Table
                    // must carry the full data (expList + hasAllMeasureSet).
                    Integer depApplyTypeCode = intVal(dep, "depApplyTypeCode");
                    if (depApplyTypeCode != null && depApplyTypeCode != 0) {
                        String depExprStr = str(dep, "depExpression");
                        List<OperationItem> depExpList = parseExpressionToExpList(depExprStr, depApplyTypeCode);
                        // Set on measure (fallback if choiceMeasure doesn't run)
                        if (child.getExpression() == null) {
                            child.setExpression(depExprStr);
                            child.setExpList(depExpList);
                        }
                        // Set on childTable (used by choiceMeasure to overwrite measure fields)
                        childTable.setExpList(depExpList);

                        // Build grandchild map from this child's own transitive deps
                        RDFNode depAppRes = dep.get("depApp");
                        if (depAppRes != null && CollectionUtils.isEmpty(childTable.getHasAllMeasureSet())) {
                            String depChildUri = depAppRes.asResource().getURI();
                            List<QuerySolution> childOwnDeps = depsByTopApp.getOrDefault(depChildUri, Collections.emptyList());
                            Map<String, Measure> gcMap = new LinkedHashMap<>();
                            for (QuerySolution gd : childOwnDeps) {
                                String gcCode = str(gd, "depMeasCode");
                                if (gcCode == null) continue;
                                Measure gc = gcMap.computeIfAbsent(gcCode, c -> {
                                    Measure m = new Measure();
                                    m.setCode(c);
                                    m.setName(str(gd, "depMeasCnName"));
                                    m.setAlias(str(gd, "depMeasEnName"));
                                    m.setMeasType(MeasureType.getTypeByCode(intVal(gd, "depMeasTypeCode")));
                                    return m;
                                });
                                Table gcTable = new Table();
                                gcTable.setSchemaName(str(gd, "depSchemaName"));
                                gcTable.setTableName(str(gd, "depTableName"));
                                gcTable.setSourceType(sourceType(intVal(gd, "depSourceTypeCode")));
                                gcTable.setFactColumn(str(gd, "depFactColumn"));
                                gcTable.setWhereCondition(str(gd, "depWhereCondition"));
                                String gcExprStr = str(gd, "depExpression");
                                gcTable.setExpression(gcExprStr);
                                Integer gcATC = intVal(gd, "depApplyTypeCode");
                                gcTable.setExpList(parseExpressionToExpList(gcExprStr, gcATC));
                                MeasureType gcType = MeasureType.getTypeByCode(gcATC);
                                gcTable.setApplyType(gcType);
                                gcTable.setMeasureType(gcType);
                                Boolean gcHasDT = boolVal(gd, "depHasColumnDT");
                                gcTable.setHasColumnDT(gcHasDT != null && gcHasDT);
                                gcTable.setConnection(buildConnection(gd, "depConnDbType", "depConnHost", "depConnPort",
                                        "depConnUser", "depConnPassword", "depConnDbName"));
                                gc.getFactTable().add(gcTable);
                            }
                            LinkedHashSet<Measure> gcSet = new LinkedHashSet<>(gcMap.values());
                            // Set on both childTable and child Measure
                            childTable.setHasAllMeasureSet(gcSet);
                            child.getHasAllMeasureSet().addAll(gcSet);
                        }
                    }
                }
                table.setHasAllMeasureSet(new LinkedHashSet<>(childMap.values()));
                measure.getHasAllMeasureSet().addAll(childMap.values());
            }

            measure.getFactTable().add(table);
        }

        // 所有 MeasureApp 都标记 available=0 才视为下线
        if (allAppsUnavailable && !processedApps.isEmpty()) {
            measure.setOnline(false);
        }

        return measure;
    }

    /**
     * Build full {@link Dimension} objects for the given set of codes.
     */
    private List<Dimension> buildDimensions(Set<String> codes) {
        String q = P +
                "SELECT DISTINCT ?dim ?dimCode ?dimCnName ?dimEnName ?dimTypeCode\n" +
                "                ?viewTypeCode ?definition ?description\n" +
                "                ?dimApp ?dimFactColumn ?whereCondition\n" +
                "                ?isMasterApp ?dimPrimaryKey ?dimColumn ?dimColumnExpr\n" +
                "                ?masterPrimaryKey ?isRootJoin\n" +
                "                ?hierarchyCode ?levelSequence ?levelCode\n" +
                "                ?factSchema ?factTable ?factSourceTypeCode\n" +
                "                ?factConnDbType ?factConnHost ?factConnPort ?factConnUser ?factConnPassword ?factConnDbName\n" +
                "                ?dimSchema ?dimTableName ?dimSourceTypeCode\n" +
                "                ?dimConnDbType ?dimConnHost ?dimConnPort ?dimConnUser ?dimConnPassword ?dimConnDbName\n" +
                "WHERE {\n" +
                "  ?dim a ind:Dimension ;\n" +
                "       ind:code ?dimCode ;\n" +
                "       ind:cnName ?dimCnName .\n" +
                "  OPTIONAL { ?dim ind:enName ?dimEnName }\n" +
                "  OPTIONAL { ?dim ind:dimTypeCode ?dimTypeCode }\n" +
                "  OPTIONAL { ?dim ind:viewTypeCode ?viewTypeCode }\n" +
                "  OPTIONAL { ?dim ind:definition ?definition }\n" +
                "  OPTIONAL { ?dim ind:description ?description }\n" +
                "  OPTIONAL { ?dim ind:hierarchyCode ?hierarchyCode }\n" +
                "  OPTIONAL { ?dim ind:levelSequence ?levelSequence }\n" +
                "  OPTIONAL { ?dim ind:levelCode ?levelCode }\n" +
                "  OPTIONAL {\n" +
                "    ?dim ind:hasDimApp ?dimApp .\n" +
                "    OPTIONAL { ?dimApp ind:dimFactColumn ?dimFactColumn }\n" +
                "    OPTIONAL { ?dimApp ind:whereCondition ?whereCondition }\n" +
                "    OPTIONAL { ?dimApp ind:isMasterApp ?isMasterApp }\n" +
                "    OPTIONAL { ?dimApp ind:dimPrimaryKey ?dimPrimaryKey }\n" +
                "    OPTIONAL { ?dimApp ind:dimColumn ?dimColumn }\n" +
                "    OPTIONAL { ?dimApp ind:dimColumnExpr ?dimColumnExpr }\n" +
                "    OPTIONAL { ?dimApp ind:masterPrimaryKey ?masterPrimaryKey }\n" +
                "    OPTIONAL { ?dimApp ind:isRootJoin ?isRootJoin }\n" +
                "    OPTIONAL {\n" +
                "      ?dimApp ind:dimFactTable ?factTbl .\n" +
                "      ?factTbl ind:schemaName ?factSchema ; ind:tableName ?factTable .\n" +
                "      OPTIONAL { ?factTbl ind:sourceTypeCode ?factSourceTypeCode }\n" +
                "      OPTIONAL {\n" +
                "        ?factTbl ind:hasConnection ?factConn .\n" +
                "        ?factConn ind:dbType ?factConnDbType ; ind:host ?factConnHost ; ind:port ?factConnPort ;\n" +
                "                  ind:dbUser ?factConnUser ; ind:dbPassword ?factConnPassword ; ind:dbName ?factConnDbName .\n" +
                "      }\n" +
                "    }\n" +
                "    OPTIONAL {\n" +
                "      ?dimApp ind:dimTable ?dimTbl .\n" +
                "      ?dimTbl ind:schemaName ?dimSchema ; ind:tableName ?dimTableName .\n" +
                "      OPTIONAL { ?dimTbl ind:sourceTypeCode ?dimSourceTypeCode }\n" +
                "      OPTIONAL {\n" +
                "        ?dimTbl ind:hasConnection ?dimConn .\n" +
                "        ?dimConn ind:dbType ?dimConnDbType ; ind:host ?dimConnHost ; ind:port ?dimConnPort ;\n" +
                "                 ind:dbUser ?dimConnUser ; ind:dbPassword ?dimConnPassword ; ind:dbName ?dimConnDbName .\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                values("dimCode", codes) + "\n" +
                "}\n";

        List<QuerySolution> rows = execSelect(q);
        Map<String, List<QuerySolution>> byCode = rows.stream()
                .collect(Collectors.groupingBy(r -> str(r, "dimCode")));

        List<Dimension> dimensions = new ArrayList<>();
        for (String code : codes) {
            List<QuerySolution> dimRows = byCode.getOrDefault(code, Collections.emptyList());
            if (dimRows.isEmpty()) {
                log.warn("[GraphIndicatorService] Dimension not found in graph: {}", code);
                continue;
            }
            dimensions.add(assembleDimension(code, dimRows));
        }
        return dimensions;
    }

    private Dimension assembleDimension(String code, List<QuerySolution> rows) {
        Dimension dim = new Dimension();
        QuerySolution first = rows.get(0);
        dim.setCode(code);
        dim.setName(str(first, "dimCnName"));
        dim.setAlias(str(first, "dimEnName"));
        dim.setDimType(DimType.findByInt(intVal(first, "dimTypeCode")).orElse(null));
        dim.setViewType(ViewType.findByInt(intVal(first, "viewTypeCode")).orElse(null));
        dim.setDefinition(str(first, "definition"));
        dim.setDescription(str(first, "description"));

        // degDim = (dimTypeCode == 0)
        Integer dimTypeCode = intVal(first, "dimTypeCode");
        if (dimTypeCode != null && dimTypeCode == 0) {
            dim.setDegDim(true);
        }

        Set<String> processedApps = new HashSet<>();

        for (QuerySolution row : rows) {
            RDFNode appNode = row.get("dimApp");
            if (appNode == null) continue;
            String appUri = appNode.asResource().getURI();
            if (!processedApps.add(appUri)) continue;

            boolean isMaster = boolVal(row, "isMasterApp") == Boolean.TRUE;
            dim.setMaster(isMaster);

            // Fact table entry
            String factSchema = str(row, "factSchema");
            String factTableName = str(row, "factTable");
            if (factSchema != null && factTableName != null) {
                Table factTable = new Table();
                factTable.setSchemaName(factSchema);
                factTable.setTableName(factTableName);
                factTable.setSourceType(sourceType(intVal(row, "factSourceTypeCode")));
                factTable.setFactColumn(str(row, "dimFactColumn"));
                factTable.setWhereCondition(str(row, "whereCondition"));
                factTable.setDimPrimaryKey(str(row, "dimPrimaryKey"));
                factTable.setDimColumn(str(row, "dimColumn"));
                factTable.setMasterPrimaryKey(str(row, "masterPrimaryKey"));
                factTable.setMaster(isMaster);

                Boolean rootJoin = boolVal(row, "isRootJoin");
                dim.setRootJoin(rootJoin == Boolean.TRUE);

                // Level / hierarchy
                String hierCode = str(row, "hierarchyCode");
                if (hierCode != null) {
                    Level level = new Level();
                    level.setHierarchyCode(hierCode);
                    level.setSequence(intVal(row, "levelSequence"));
                    level.setCode(str(row, "levelCode"));
                    dim.setLevel(level);
                }

                factTable.setConnection(buildConnection(row, "factConnDbType", "factConnHost", "factConnPort",
                        "factConnUser", "factConnPassword", "factConnDbName"));
                dim.getFactTableList().add(factTable);
            }

            // Dimension table entry (STD_WITH_TABLE only)
            String dimSchema = str(row, "dimSchema");
            String dimTblName = str(row, "dimTableName");
            if (dimSchema != null && dimTblName != null) {
                Table dimTable = new Table();
                dimTable.setSchemaName(dimSchema);
                dimTable.setTableName(dimTblName);
                dimTable.setSourceType(sourceType(intVal(row, "dimSourceTypeCode")));
                String dimPrimaryKey = str(row, "dimPrimaryKey");
                String dimColumn = str(row, "dimColumn");
                String dimColumnExpr = str(row, "dimColumnExpr");
                String masterPrimaryKey = str(row, "masterPrimaryKey");
                // TTL 可能没用 dimPrimaryKey 而是用 masterPrimaryKey，做 fallback
                if (dimPrimaryKey == null && masterPrimaryKey != null) {
                    dimPrimaryKey = masterPrimaryKey;
                }
                dimTable.setDimPrimaryKey(dimPrimaryKey);
                dimTable.setDimColumn(dimColumn);
                dimTable.setDimColumnExpr(dimColumnExpr);
                dimTable.setMaster(isMaster);
                dimTable.setConnection(buildConnection(row, "dimConnDbType", "dimConnHost", "dimConnPort",
                        "dimConnUser", "dimConnPassword", "dimConnDbName"));
                // Populate columnList so SqlCheckServiceImpl.hasFactColumnByDimTable() can verify masterPrimaryKey
                List<DwColumn> dimColList = new ArrayList<>();
                if (dimPrimaryKey != null) {
                    DwColumn keyCol = new DwColumn();
                    keyCol.setName(dimPrimaryKey);
                    dimColList.add(keyCol);
                }
                if (dimColumn != null && !dimColumn.equalsIgnoreCase(dimPrimaryKey)) {
                    DwColumn valCol = new DwColumn();
                    valCol.setName(dimColumn);
                    dimColList.add(valCol);
                }
                // Also include masterPrimaryKey for validation (may differ from dimPrimaryKey)
                if (masterPrimaryKey != null
                        && !masterPrimaryKey.equalsIgnoreCase(dimPrimaryKey)
                        && !masterPrimaryKey.equalsIgnoreCase(dimColumn)) {
                    DwColumn mpkCol = new DwColumn();
                    mpkCol.setName(masterPrimaryKey);
                    dimColList.add(mpkCol);
                }
                dimTable.setColumnList(dimColList);
                dim.getDimTableList().add(dimTable);
            }
        }
        return dim;
    }

    /**
     * 公共维度映射：从知识图谱读取 ind:NaturalDimMapping，
     * 将每个指标的 dimMeasTableColumnList 填充完整，使 getDimFkId() 能正确替换物理列。
     *
     * <p>匹配规则（与 BuildSqlServiceImpl.getDimFkId 对应）：
     * <ul>
     *   <li>{@code naturalHierarchyCode} — 匹配同一层次体系内所有维度（适用于日期等多粒度维度）</li>
     *   <li>{@code naturalDimCode}       — 按维度 code 精确匹配（适用于门店等无层次维度）</li>
     * </ul>
     */
    private void buildNaturalDimMappings(Set<Measure> measures, Set<Dimension> dimensions) {
        Set<String> measCodes = measures.stream()
                .map(Measure::getCode).collect(Collectors.toCollection(LinkedHashSet::new));

        String sparql = P +
                "SELECT ?measCode ?naturalHierCode ?naturalDimCode ?physicalColumn\n" +
                "       ?schemaName ?tableName\n" +
                "WHERE {\n" +
                "  ?measNode a ind:Measure ; ind:code ?measCode .\n" +
                "  ?measNode ind:hasMeasureApp ?app .\n" +
                "  ?app ind:appliesToTable ?tbl .\n" +
                "  ?tbl ind:schemaName ?schemaName ; ind:tableName ?tableName .\n" +
                "  ?app ind:hasNaturalDimMapping ?ndm .\n" +
                "  ?ndm ind:physicalColumn ?physicalColumn .\n" +
                "  OPTIONAL { ?ndm ind:naturalHierarchyCode ?naturalHierCode }\n" +
                "  OPTIONAL { ?ndm ind:naturalDimCode ?naturalDimCode }\n" +
                values("measCode", measCodes) + "\n" +
                "}\n";

        List<QuerySolution> rows = execSelect(sparql);
        if (rows.isEmpty()) return;

        // 按 measCode 分组
        Map<String, List<QuerySolution>> byMeas = rows.stream()
                .collect(Collectors.groupingBy(r -> str(r, "measCode")));

        for (Measure measure : measures) {
            List<QuerySolution> mappingRows = byMeas.get(measure.getCode());
            if (mappingRows == null || mappingRows.isEmpty()) continue;

            for (QuerySolution row : mappingRows) {
                String physicalColumn = str(row, "physicalColumn");
                String schemaName    = str(row, "schemaName");
                String tableName     = str(row, "tableName");
                String hierCode      = str(row, "naturalHierCode");
                String dimCode       = str(row, "naturalDimCode");

                if (physicalColumn == null) continue;

                // 按层次匹配：找出 dimensions 中所有 hierarchyCode 相同的维度
                if (hierCode != null) {
                    for (Dimension dim : dimensions) {
                        if (dim.getLevel() != null
                                && hierCode.equalsIgnoreCase(dim.getLevel().getHierarchyCode())) {
                            measure.getDimMeasTableColumnList().add(
                                    buildDimMeasTableColumn(dim.getCode(), schemaName, tableName, physicalColumn));
                        }
                    }
                }

                // 按 code 精确匹配
                if (dimCode != null) {
                    for (Dimension dim : dimensions) {
                        if (dimCode.equalsIgnoreCase(dim.getCode())) {
                            measure.getDimMeasTableColumnList().add(
                                    buildDimMeasTableColumn(dimCode, schemaName, tableName, physicalColumn));
                        }
                    }
                }
            }
        }
    }

    private static DimMeasTableColumn buildDimMeasTableColumn(String dimCode, String schemaName,
                                                               String table, String column) {
        DimMeasTableColumn col = new DimMeasTableColumn();
        col.setDimCode(dimCode);
        col.setSchemaName(schemaName);
        col.setTable(table);
        col.setColumn(column);
        return col;
    }

    // ── Simple list builders (no table info) ─────────────────────────────────

    private List<Measure> buildMeasureBaseList(List<QuerySolution> rows) {
        Map<String, Measure> seen = new LinkedHashMap<>();
        for (QuerySolution r : rows) {
            String code = str(r, "code");
            if (code == null || seen.containsKey(code)) continue;
            Measure m = new Measure();
            m.setCode(code);
            m.setName(str(r, "cnName"));
            m.setAlias(str(r, "enName"));
            m.setMeasType(MeasureType.getTypeByCode(intVal(r, "measTypeCode")));
            m.setDefinition(str(r, "definition"));
            m.setDescription(str(r, "description"));
            seen.put(code, m);
        }
        return new ArrayList<>(seen.values());
    }

    private List<Dimension> buildDimensionBaseList(List<QuerySolution> rows) {
        Map<String, Dimension> seen = new LinkedHashMap<>();
        for (QuerySolution r : rows) {
            String code = str(r, "code");
            if (code == null || seen.containsKey(code)) continue;
            Dimension d = new Dimension();
            d.setCode(code);
            d.setName(str(r, "cnName"));
            d.setAlias(str(r, "enName"));
            d.setDimType(DimType.findByInt(intVal(r, "dimTypeCode")).orElse(null));
            d.setViewType(ViewType.findByInt(intVal(r, "viewTypeCode")).orElse(null));
            d.setDefinition(str(r, "definition"));
            d.setDescription(str(r, "description"));
            Integer dtc = intVal(r, "dimTypeCode");
            if (dtc != null && dtc == 0) d.setDegDim(true);
            seen.put(code, d);
        }
        return new ArrayList<>(seen.values());
    }

    // =========================================================================
    // SPARQL execution helpers
    // =========================================================================

    private List<QuerySolution> execSelect(String sparql) {
        try (QueryExecution qe = QueryExecutionFactory.create(
                QueryFactory.create(sparql), graphStore.getModel())) {
            ResultSet rs = qe.execSelect();
            List<QuerySolution> rows = new ArrayList<>();
            while (rs.hasNext()) rows.add(rs.next());
            return rows;
        } catch (Exception e) {
            log.error("[GraphIndicatorService] SPARQL SELECT failed: {}", e.getMessage());
            log.debug("[GraphIndicatorService] Query:\n{}", sparql);
            return Collections.emptyList();
        }
    }

    private boolean execAsk(String sparql) {
        try (QueryExecution qe = QueryExecutionFactory.create(
                QueryFactory.create(sparql), graphStore.getModel())) {
            return qe.execAsk();
        } catch (Exception e) {
            log.error("[GraphIndicatorService] SPARQL ASK failed: {}", e.getMessage());
            log.debug("[GraphIndicatorService] Query:\n{}", sparql);
            return false;
        }
    }

    // =========================================================================
    // Value extraction helpers
    // =========================================================================

    /** Get literal as String, or null. */
    private static String str(QuerySolution row, String var) {
        RDFNode n = row.get(var);
        if (n == null) return null;
        return n.isLiteral() ? n.asLiteral().getString() : n.asResource().getLocalName();
    }

    /** Get literal as Integer, or null. */
    private static Integer intVal(QuerySolution row, String var) {
        RDFNode n = row.get(var);
        if (n == null || !n.isLiteral()) return null;
        return n.asLiteral().getInt();
    }

    /** Get literal as Long, or null. */
    private static Long longVal(QuerySolution row, String var) {
        RDFNode n = row.get(var);
        if (n == null || !n.isLiteral()) return null;
        return n.asLiteral().getLong();
    }

    /** Get literal as Boolean, or null. */
    private static Boolean boolVal(QuerySolution row, String var) {
        RDFNode n = row.get(var);
        if (n == null || !n.isLiteral()) return null;
        return n.asLiteral().getBoolean();
    }

    /** Escape a string literal for inline embedding in a SPARQL query. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /**
     * Build a SPARQL {@code VALUES} clause for a string-valued variable.
     * e.g. {@code VALUES ?code { "a" "b" }}
     */
    private static String values(String var, Collection<String> vals) {
        if (vals == null || vals.isEmpty()) {
            return "VALUES ?" + var + " { }";
        }
        StringBuilder sb = new StringBuilder("VALUES ?").append(var).append(" {");
        for (String v : vals) {
            sb.append(" \"").append(esc(v)).append('"');
        }
        return sb.append(" }").toString();
    }

    /**
     * Build a SPARQL {@code VALUES} clause for a URI-valued variable.
     * e.g. {@code VALUES ?topApp { <uri1> <uri2> }}
     */
    private static String valuesUri(String var, Collection<String> uris) {
        if (uris == null || uris.isEmpty()) {
            return "VALUES ?" + var + " { }";
        }
        StringBuilder sb = new StringBuilder("VALUES ?").append(var).append(" {");
        for (String u : uris) {
            sb.append(" <").append(u).append('>');
        }
        return sb.append(" }").toString();
    }

    /**
     * Build a {@link DataConnection} from SPARQL row variables; returns null if dbType is absent.
     */
    private static DataConnection buildConnection(QuerySolution row,
                                                  String dbTypeVar, String hostVar, String portVar,
                                                  String userVar, String passwordVar, String dbNameVar) {
        String dbType = str(row, dbTypeVar);
        if (dbType == null) return null;
        DataConnection conn = new DataConnection();
        conn.setDbType(dbType);
        conn.setHost(str(row, hostVar));
        RDFNode portNode = row.get(portVar);
        conn.setPort(portNode != null && portNode.isLiteral() ? portNode.asLiteral().getInt() : 3306);
        conn.setDbUser(str(row, userVar));
        conn.setDbPassword(str(row, passwordVar));
        conn.setDbName(str(row, dbNameVar));
        return conn;
    }

    /** Map sourceTypeCode integer to {@link SourceType}, defaulting to MYSQL. */
    private static SourceType sourceType(Integer code) {
        if (code != null && code == 1) return SourceType.DORIS;
        return SourceType.MYSQL;
    }

    /**
     * 将 TTL 中的 expression 字段解析为 List&lt;OperationItem&gt;。
     * <ul>
     *   <li>applyTypeCode=0 (原子指标)：expression 为 JSON 数组，直接反序列化。</li>
     *   <li>applyTypeCode=1 (衍生指标)：expression 为公式字符串（如 "MEAS_a / MEAS_b"），
     *       按空格分词后构造 OperationItem 列表。</li>
     * </ul>
     */
    private static List<OperationItem> parseExpressionToExpList(String expression, Integer applyTypeCode) {
        if (expression == null || expression.trim().isEmpty()) return null;

        // JSON 数组格式（原子指标 / 复合指标）
        if (expression.trim().startsWith("[")) {
            List<OperationItem> list = JSON.parseArray(expression, OperationItem.class);
            return (list != null && !list.isEmpty()) ? list : null;
        }

        // 公式字符串格式（衍生指标，applyTypeCode=1）
        Set<String> operators = new HashSet<>(Arrays.asList("+", "-", "*", "/", "(", ")"));
        List<OperationItem> items = new ArrayList<>();
        for (String token : expression.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            OperationItem item = new OperationItem();
            if (operators.contains(token)) {
                item.setOperatingType(OperationItem.OPERATOR);
                item.setOperator(token);
            } else if (token.matches("-?\\d+(\\.\\d+)?")) {
                item.setOperatingType(OperationItem.CONSTANT);
                item.setConstant(Double.parseDouble(token));
            } else {
                item.setOperatingType(OperationItem.OPERAND);
                item.setOperand(new OperationItem.MeasureBasicInfo(null, token, null));
            }
            items.add(item);
        }
        return items.isEmpty() ? null : items;
    }
}
