package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.model.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DA-owned query boundary for the call-SOP diagnosis and workbench pages.
 *
 * The endpoint deliberately exposes only a fixed column/filter whitelist. AD
 * remains responsible for SOP interpretation and presentation, while all
 * source-record reads go through DA instead of opening a second database
 * connection from the web application.
 */
@RestController
@RequestMapping("/indicator/api/v1/call-sop")
public class CallSopQueryController {

    private static final String DEFAULT_DAY = "2026-07-02";
    private static final String DEFAULT_STORE = "小鹏汽车杭州演示体验中心";

    private static final Map<String, String> FILTER_COLUMNS;

    static {
        Map<String, String> columns = new HashMap<>();
        columns.put("ad.date_day", "f.activity_date");
        columns.put("ad.store", "f.store_name");
        columns.put("ad.store_city", "f.store_city");
        columns.put("ad.store_manager", "f.manager_name");
        columns.put("ad.sales_expert", "f.expert_name");
        columns.put("ad.intent", "COALESCE(j.intent_name, f.intent_name)");
        columns.put("ad.quality_score_level", "f.quality_score_level");
        columns.put("ad.quality_issue_category", "f.issue_category");
        columns.put("ad.quality_pass", "f.quality_pass_label");
        columns.put("ad.call_flow_total", "f.call_flow_total");
        columns.put("ad.quality_rule", "COALESCE(r.sop_category_name, r.sop_category_code, f.rule_id)");
        FILTER_COLUMNS = Collections.unmodifiableMap(columns);
    }

    private final JdbcTemplate jdbcTemplate;

    public CallSopQueryController(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/records")
    public Response<Map<String, Object>> records(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Collections.emptyMap() : body;
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        boolean hasDay = false;
        boolean hasStore = false;

        Object rawFilters = payload.get("filters");
        if (rawFilters instanceof List) {
            for (Object rawFilter : (List<?>) rawFilters) {
                if (!(rawFilter instanceof Map)) {
                    continue;
                }
                Map<?, ?> filter = (Map<?, ?>) rawFilter;
                String member = stringValue(filter.get("member"));
                if (member.isEmpty()) {
                    member = stringValue(filter.get("code"));
                }
                String column = FILTER_COLUMNS.get(member);
                if (column == null) {
                    continue;
                }
                List<String> values = filterValues(filter);
                if (values.isEmpty()) {
                    continue;
                }
                hasDay = hasDay || "ad.date_day".equals(member);
                hasStore = hasStore || "ad.store".equals(member);
                String operator = stringValue(filter.get("operator")).toLowerCase();
                if (Arrays.asList("not_equals", "neq", "!=").contains(operator)) {
                    where.add(column + " <> ?");
                    args.add(values.get(0));
                    continue;
                }
                where.add(column + " IN (" + placeholders(values.size()) + ")");
                args.addAll(values);
            }
        }
        if (!hasDay) {
            where.add("f.activity_date = ?");
            args.add(DEFAULT_DAY);
        }
        if (!hasStore) {
            where.add("f.store_name = ?");
            args.add(DEFAULT_STORE);
        }

        String sql = "SELECT "
                + "f.quality_id, f.activity_date, f.store_name, f.store_city, f.manager_name, "
                + "f.expert_name, f.specialist_id, f.quality_score_level, f.quality_pass_label, "
                + "f.call_flow_total, f.issue_category, f.total_score, f.slot_coverage_rate, "
                + "COALESCE(r.sop_category_name, r.sop_category_code, f.rule_id) AS quality_rule, "
                + "f.low_quality_call_count, f.low_coverage_call_count, f.missing_slot_count, "
                + "f.intent_name AS grouped_intent_name, j.customer_account_id, "
                + "j.latest_conversation_time, j.aggregated_content, j.intent_name, "
                + "j.intent_original_name, j.actual_next_action, j.validation_notes, "
                + "j.sop_checkpoints_json, j.sop_analysis_version, j.call_asr_segments_json, "
                + "j.call_sop_evidence_json, j.call_quality_detail_json, j.call_word_count, "
                + "j.call_duration_seconds, j.invite_result_label, j.sop_grade_label, "
                + "j.primary_sop_category, j.call_workspace_version, j.sop_connected_flag, "
                + "j.sop_hit_checkpoint_count, j.sop_total_checkpoint_count "
                + "FROM da_tms.im_call_quality_fact f "
                + "JOIN da_tms.call_record_judgement_results j ON j.id = f.quality_id "
                + "LEFT JOIN da_tms.call_record_judgement_rules r ON r.rule_id = f.rule_id "
                + "WHERE " + String.join(" AND ", where) + " "
                + "ORDER BY f.expert_name, j.latest_conversation_time DESC, f.quality_id DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", "DA");
        data.put("rows", rows);
        return Response.ok(data);
    }

    private static List<String> filterValues(Map<?, ?> filter) {
        Object raw = filter.get("values");
        if (raw == null) {
            raw = filter.get("value");
        }
        List<String> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                String value = stringValue(item);
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        } else {
            String value = stringValue(raw);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String placeholders(int size) {
        return String.join(", ", Collections.nCopies(size, "?"));
    }
}
