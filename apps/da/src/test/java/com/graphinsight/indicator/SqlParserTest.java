package com.graphinsight.indicator;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.util.JdbcConstants;

import java.util.List;

public class SqlParserTest {

    public static void main(String[] args) {

        String sql1 = "acct=1 and bcc='3' and decode(ffff)=33";
        String whereSql = setAlias(sql1, "t0");
        /*
        String sql = "select * from dual where acct=1 and bcc='3' and decode(ffff)=33";
        DbType dbType = JdbcConstants.MYSQL;
        List<SQLStatement> statementList = SQLUtils.parseStatements(sql, dbType);

        for (SQLStatement statement : statementList) {
            List<SQLObject> sqlObjectList = statement.getChildren();
            for (SQLObject sqlObject : sqlObjectList) {
                SQLSelect sqlSelect = (SQLSelect) sqlObject;
                MySqlSelectQueryBlock sqlSelectQuery = (MySqlSelectQueryBlock)sqlSelect.getQuery();
                SQLBinaryOpExpr sqlExpr = (SQLBinaryOpExpr)sqlSelectQuery.getWhere();
                analysis(sqlExpr);
                String whereSql = sqlExpr.toString().replaceAll("\n\t", " ");
                System.out.println(sqlExpr);
            }

        }*/


        System.out.println(whereSql);

    }

    public static String setAlias(String sql, String alias) {
        DbType dbType = JdbcConstants.MYSQL;
        sql = "select * from dual where " + sql;
        List<SQLStatement> statementList = SQLUtils.parseStatements(sql, dbType);
        String whereSql = "";
        for (SQLStatement statement : statementList) {
            List<SQLObject> sqlObjectList = statement.getChildren();
            for (SQLObject sqlObject : sqlObjectList) {
                SQLSelect sqlSelect = (SQLSelect) sqlObject;
                MySqlSelectQueryBlock sqlSelectQuery = (MySqlSelectQueryBlock)sqlSelect.getQuery();
                SQLBinaryOpExpr sqlExpr = (SQLBinaryOpExpr)sqlSelectQuery.getWhere();
                analysis(sqlExpr, alias);
                whereSql = sqlExpr.toString().replaceAll("\n\t", " ");
                System.out.println(sqlExpr);
            }

        }

        return whereSql;
    }

    private static void analysis(SQLBinaryOpExpr sqlExper, String alias) {

        SQLExpr rightSqlExpr = sqlExper.getRight();

        if (rightSqlExpr instanceof SQLBinaryOpExpr) {
            analysis((SQLBinaryOpExpr)rightSqlExpr, alias);
        } else {
            visitSqlMethod(rightSqlExpr, alias);
        }

        SQLExpr leftSqlExpr = sqlExper.getLeft();
        if (leftSqlExpr instanceof SQLBinaryOpExpr) {
            analysis((SQLBinaryOpExpr)leftSqlExpr, alias);
        } else {
            visitSqlMethod(leftSqlExpr, alias);
        }
    }

    private static void visitSqlMethod(SQLExpr sqlExpr, String alias) {

        if (sqlExpr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr method = (SQLMethodInvokeExpr)sqlExpr;
            List<SQLExpr> arguments = method.getArguments();
            for (SQLExpr argument : arguments) {

                if (argument instanceof SQLIdentifierExpr) {
                    SQLIdentifierExpr expr = (SQLIdentifierExpr)argument;
                    expr.setName(alias + "." + expr.getName());
                    System.out.println(expr.getName());
                }

            }
        } else if (sqlExpr instanceof SQLIntegerExpr) {
            SQLIntegerExpr expr = (SQLIntegerExpr)sqlExpr;
        } else if (sqlExpr instanceof SQLCharExpr) {
            SQLCharExpr expr = (SQLCharExpr)sqlExpr;
        } else if (sqlExpr instanceof SQLIdentifierExpr) {
            SQLIdentifierExpr expr = (SQLIdentifierExpr)sqlExpr;
            expr.setName(alias + "." + expr.getName());
        }

    }

}

