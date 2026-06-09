package com.graphinsight.indicator.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.util.ImmutableBitSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 内存数据表,用于构建数据回来的临时数据。
 */
public class MemorySchema extends AbstractSchema {

    private ImmutableMap<String, Table> tables;

    private final ImmutableMap.Builder<String, Table> builder = ImmutableMap.<String, Table>builder();

    private final ImmutableBitSet pkColumns = ImmutableBitSet.of(0);

    public MemorySchema() {
    }

    public void addTable(String name, List<ColumnTypeInfo> typeInfoList, List<Object[]> data) {

        Function<RelDataTypeFactory, RelDataType> function = new ColumnFunction(typeInfoList);
        Table table = new PkClusteredTable(function, pkColumns, data);

        builder.put(name, table);

    }

    public void build() {
        this.tables = this.builder.build();
    }

    @Override protected Map<String, Table> getTableMap() {
        return tables;
    }

    public void addTable(String name, TempTable tempTable) {
        this.addTable(name, tempTable.getColumnTypeInfoList(), tempTable.getDataList());
    }

    /**
     * A table sorted (ascending direction and nulls last) on the primary key.
     */
    private static class PkClusteredTable extends AbstractTable implements ScannableTable {
        private final ImmutableBitSet pkColumns;
        private final List<Object[]> data;
        private final Function<RelDataTypeFactory, RelDataType> typeBuilder;

        PkClusteredTable(
                Function<RelDataTypeFactory, RelDataType> dataTypeBuilder,
                ImmutableBitSet pkColumns,
                List<Object[]> data) {
            this.typeBuilder = dataTypeBuilder;
            this.pkColumns = pkColumns;
            this.data = data;
        }

        @Override public Statistic getStatistic() {
            List<RelFieldCollation> collationFields = new ArrayList<>();
            for (Integer key : pkColumns) {
                collationFields.add(
                        new RelFieldCollation(
                                key,
                                RelFieldCollation.Direction.ASCENDING,
                                RelFieldCollation.NullDirection.LAST));
            }
            return Statistics.of(data.size(), ImmutableList.of(pkColumns),
                    ImmutableList.of(RelCollations.of(collationFields)));
        }

        @Override public RelDataType getRowType(final RelDataTypeFactory typeFactory) {
            return typeBuilder.apply(typeFactory);
        }

        @Override public Enumerable<Object[]> scan(final DataContext root) {
            return Linq4j.asEnumerable(data);
        }

    }

}
