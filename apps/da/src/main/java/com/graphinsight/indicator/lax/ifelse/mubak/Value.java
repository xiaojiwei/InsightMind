package com.graphinsight.indicator.lax.ifelse.mubak;

/**
 * Author: lixiaolong
 * Date: 2023/6/6
 * Desc:
 */
public class Value {
    public static Value VOID = new Value(new Object());

    final Object value;

    public Value(Object value) {
        this.value = value;
    }

    public Boolean asBoolean() {
        return (Boolean)value;
    }

    public Double asDouble() {
        return (Double)value;
    }

    public String asString() {
        return String.valueOf(value);
    }

    public boolean isDouble() {
        return value instanceof Double;
    }

    @Override
    public int hashCode() {

        if(value == null) {
            return 0;
        }

        return this.value.hashCode();
    }

    @Override
    public boolean equals(Object o) {

        if(value == o) {
            return true;
        }

        if(value == null || o == null || o.getClass() != this.getClass()) {
            return false;
        }

        Value that = (Value)o;

        return this.value.equals(that.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
