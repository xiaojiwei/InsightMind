package com.graphinsight.indicator.graph;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GraphReasoningServiceImpl implements GraphReasoningService {

    private static final String P = IndicatorOntology.PREFIXES;

    @Resource
    private GraphStore graphStore;

    @Override
    public List<ReasoningRelationDTO> listCompatibleDimensions(String measureCode) {
        return listRelations(
                measureCode,
                "ind:Measure",
                "ind:Dimension",
                "ind:compatibleDimension",
                "compatibleDimension");
    }

    @Override
    public List<ReasoningRelationDTO> listUpstreamMeasures(String measureCode) {
        return listRelations(
                measureCode,
                "ind:Measure",
                "ind:Measure",
                "ind:upstreamMeasure",
                "upstreamMeasure");
    }

    @Override
    public List<ReasoningRelationDTO> listDownstreamMeasures(String measureCode) {
        return listRelations(
                measureCode,
                "ind:Measure",
                "ind:Measure",
                "ind:downstreamMeasure",
                "downstreamMeasure");
    }

    private List<ReasoningRelationDTO> listRelations(
            String sourceCode,
            String sourceType,
            String targetType,
            String property,
            String relation) {
        String sparql = P + "\n" +
                "SELECT DISTINCT ?sourceCode ?sourceName ?targetCode ?targetName ?ruleId ?confidence ?evidencePath WHERE {\n" +
                "  ?source a " + sourceType + " ; ind:code \"" + esc(sourceCode) + "\" ; " + property + " ?target .\n" +
                "  ?source ind:code ?sourceCode .\n" +
                "  OPTIONAL { ?source ind:cnName ?sourceName . }\n" +
                "  ?target a " + targetType + " ; ind:code ?targetCode .\n" +
                "  OPTIONAL { ?target ind:cnName ?targetName . }\n" +
                "  OPTIONAL {\n" +
                "    ?inf rdf:subject ?source ; rdf:predicate " + property + " ; rdf:object ?target .\n" +
                "    OPTIONAL { ?inf ind:inferredByRule ?ruleId . }\n" +
                "    OPTIONAL { ?inf ind:confidence ?confidence . }\n" +
                "    OPTIONAL { ?inf ind:evidencePath ?evidencePath . }\n" +
                "  }\n" +
                "} ORDER BY ?targetCode";
        List<ReasoningRelationDTO> rows = new ArrayList<>();
        for (QuerySolution row : execSelect(sparql)) {
            ReasoningRelationDTO dto = new ReasoningRelationDTO();
            dto.setSourceCode(str(row, "sourceCode"));
            dto.setSourceName(str(row, "sourceName"));
            dto.setSourceType(stripPrefix(sourceType));
            dto.setTargetCode(str(row, "targetCode"));
            dto.setTargetName(str(row, "targetName"));
            dto.setTargetType(stripPrefix(targetType));
            dto.setRelation(relation);
            dto.setRuleId(str(row, "ruleId"));
            dto.setConfidence(decimal(row, "confidence"));
            dto.setEvidencePath(str(row, "evidencePath"));
            rows.add(dto);
        }
        return rows;
    }

    private List<QuerySolution> execSelect(String sparql) {
        try (QueryExecution qe = QueryExecutionFactory.create(
                QueryFactory.create(sparql), graphStore.getModel())) {
            ResultSet rs = qe.execSelect();
            List<QuerySolution> rows = new ArrayList<>();
            while (rs.hasNext()) {
                rows.add(rs.next());
            }
            return rows;
        } catch (Exception e) {
            log.error("[GraphReasoningService] SPARQL SELECT failed: {}", e.getMessage());
            log.debug("[GraphReasoningService] Query:\n{}", sparql);
            return new ArrayList<>();
        }
    }

    private static String str(QuerySolution row, String var) {
        RDFNode n = row.get(var);
        if (n == null) {
            return null;
        }
        return n.isLiteral() ? n.asLiteral().getString() : n.asResource().getLocalName();
    }

    private static BigDecimal decimal(QuerySolution row, String var) {
        RDFNode n = row.get(var);
        if (n == null || !n.isLiteral()) {
            return null;
        }
        try {
            return new BigDecimal(n.asLiteral().getLexicalForm());
        } catch (Exception ignored) {
            return BigDecimal.valueOf(n.asLiteral().getDouble());
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String stripPrefix(String type) {
        int idx = type.indexOf(':');
        return idx >= 0 ? type.substring(idx + 1) : type;
    }
}
