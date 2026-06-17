package com.graphinsight.indicator.graph;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GraphReasoningServiceImplTest {

    @Test
    public void shouldReadMaterializedReasoningTriples() throws Exception {
        Path dir = Files.createTempDirectory("graph-reasoning");
        Path data = dir.resolve("indicator-data.ttl");
        Path inferred = dir.resolve("indicator-inferred.ttl");

        Files.write(data, (
                "@prefix ind: <http://indicator.insightmind.com/ontology#> .\n" +
                "@prefix inst: <http://indicator.insightmind.com/instance/> .\n" +
                "inst:meas_order_cnt a ind:Measure ; ind:code \"MEAS_order_cnt\" ; ind:cnName \"订单数\" .\n" +
                "inst:dim_city a ind:Dimension ; ind:code \"DIM_city\" ; ind:cnName \"城市\" .\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(inferred, (
                "@prefix ind: <http://indicator.insightmind.com/ontology#> .\n" +
                "@prefix inst: <http://indicator.insightmind.com/instance/> .\n" +
                "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
                "inst:meas_order_cnt ind:compatibleDimension inst:dim_city .\n" +
                "inst:inf_1 a ind:Inference ;\n" +
                "  rdf:subject inst:meas_order_cnt ;\n" +
                "  rdf:predicate ind:compatibleDimension ;\n" +
                "  rdf:object inst:dim_city ;\n" +
                "  ind:inferredByRule \"compatible_dimension.shared_fact_table\" ;\n" +
                "  ind:confidence 1.0 ;\n" +
                "  ind:evidencePath \"meas -> table <- dim\" .\n"
        ).getBytes(StandardCharsets.UTF_8));

        GraphStore graphStore = new GraphStore();
        ReflectionTestUtils.setField(graphStore, "dataPath", data.toString());
        ReflectionTestUtils.setField(graphStore, "inferredDataPath", inferred.toString());
        graphStore.init();

        GraphReasoningServiceImpl service = new GraphReasoningServiceImpl();
        ReflectionTestUtils.setField(service, "graphStore", graphStore);

        List<ReasoningRelationDTO> rows = service.listCompatibleDimensions("MEAS_order_cnt");

        assertEquals(1, rows.size());
        assertEquals("MEAS_order_cnt", rows.get(0).getSourceCode());
        assertEquals("DIM_city", rows.get(0).getTargetCode());
        assertEquals("compatibleDimension", rows.get(0).getRelation());
        assertEquals("compatible_dimension.shared_fact_table", rows.get(0).getRuleId());
        assertNotNull(rows.get(0).getConfidence());
    }
}
