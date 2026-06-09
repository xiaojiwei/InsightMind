package com.graphinsight.indicator.lax.filter;

import lombok.Data;

@Data
public class Node {

    public Object result;

    public Boolean condition() {
        if (null != this.result) {
            return (Boolean)this.result;
        }
        return false;
    }



    public Double numberic() {
        if (null != this.result) {
            return Double.valueOf(this.result.toString());
        }
        return Double.valueOf(0);
    }

}
