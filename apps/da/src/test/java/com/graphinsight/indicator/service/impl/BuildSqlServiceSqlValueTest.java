package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.enums.SqlLogicalType;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.Operator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildSqlServiceSqlValueTest {

    @Test
    void inValuesEscapeSqlInjectionPayload() throws Exception {
        String payload = "x') OR 1=1 --";

        String sqlValues = getSqlValue(payload);

        assertEquals("'x'') OR 1=1 --'", sqlValues);
    }

    @Test
    void inValuesEscapeEveryApostropheContainingString() throws Exception {
        String first = "O'Reilly";
        String second = "customer's choice";

        String sqlValues = getSqlValue(first, second);

        assertEquals(
                "'O''Reilly','customer''s choice'",
                sqlValues
        );
    }

    @Test
    void inValuesPreserveQuotedNumericAndDateSemantics() throws Exception {
        String integer = "42";
        String decimal = "3.14";
        String date = "2026-08-19";

        assertEquals("42", BuildSqlServiceImpl.formatSqlValue(integer));
        assertEquals(
                "'42','3.14','2026-08-19'",
                getSqlValue(integer, decimal, date)
        );
    }

    @Test
    void inValuesRemainQuotedWithHashAndBackslashPayloads() throws Exception {
        assertEquals(
                "'a#comment','C:\\tmp','quote''and\\slash'",
                getSqlValue("a#comment", "C:\\tmp", "quote'and\\slash")
        );
    }

    @Test
    void inValuesPreserveUnicodeWithoutBackslashEscapes() throws Exception {
        assertEquals("'东区'", getSqlValue("东区"));
        assertEquals("O''Reilly", BuildSqlServiceImpl.formatSqlValue("O'Reilly"));
    }

    @Test
    void betweenValuesKeepInjectionPayloadInsideQuotedLiterals() throws Exception {
        Operator operator = new Operator();
        operator.setSqlOprType(SqlOprType.BETEEN);
        operator.setSqlLogicalType(SqlLogicalType.AND);
        operator.setBegin("2026-01-01' OR 1=1 --");
        operator.setEnd("2026-12-31' OR 1=1 --");

        assertEquals(
                "(m.ratio>= '2026-01-01'' OR 1=1 --' and "
                        + "m.ratio<= '2026-12-31'' OR 1=1 --')",
                buildMeasureOperator(operator)
        );
    }

    @Test
    void measureNumericOperatorRejectsSqlExpressionPayload() throws Exception {
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> getSqlOneValue("0 OR 1=1")
        );

        assertEquals(IndicatorParamNotValidException.class, error.getCause().getClass());
        assertEquals("指标过滤值必须是合法数值", error.getCause().getMessage());
    }

    @Test
    void measureNumericOperatorPreservesValidNumericLiterals() throws Exception {
        assertEquals("-12.50", getSqlOneValue(" -12.50 "));
        assertEquals("1e3", getSqlOneValue("1e3"));
        assertEquals("0", getSqlOneValue());
    }

    private String getSqlValue(String... values) throws Exception {
        Operator operator = new Operator();
        operator.setDataList(Arrays.asList(values));

        Method method = BuildSqlServiceImpl.class.getDeclaredMethod("getSqlValue", Operator.class);
        method.setAccessible(true);
        return (String) method.invoke(new BuildSqlServiceImpl(), operator);
    }

    private String getSqlOneValue(String... values) throws Exception {
        Operator operator = new Operator();
        operator.setDataList(Arrays.asList(values));

        Method method = BuildSqlServiceImpl.class.getDeclaredMethod("getSqlOneValue", Operator.class);
        method.setAccessible(true);
        return (String) method.invoke(new BuildSqlServiceImpl(), operator);
    }

    private String buildMeasureOperator(Operator operator) throws Exception {
        Method method = BuildSqlServiceImpl.class.getDeclaredMethod(
                "builMeasOperator", Operator.class, boolean.class, String.class, String.class
        );
        method.setAccessible(true);
        return (String) method.invoke(new BuildSqlServiceImpl(), operator, true, "m", "ratio");
    }
}
