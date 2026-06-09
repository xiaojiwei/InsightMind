package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.List;

@Data
public class FieldGroupConfigure extends BaseModel {
    private List<Measure> leftMeasure;
    private List<Measure> rightMeasure;

    @Data
    public static class Measure {

        private int id;
        private String cnName;
        private String enName;
        private String code;
        private String viewType;
        private String description;
        private int leafCategoryId;
        private String type;
        private String parentId;
        private String dimValueCount;
        private String sequence;
        private String hierarchyId;
        private String levelSequence;
        private int online;
        private String offlineReason;
        private boolean belongSpace;
        private boolean hasAuth;
        private boolean ineffective;
        private Config config;
    }

    @Data
    public static class Config {

        private String alias;
        private List<String> ratioConfigList;
        private Format format;
       // private Order order;
    }

    @Data
    public static class Format {

        private int type;
        private int decimalPlaces;
        private int dataScale;
        private boolean useThousandths;
    }
}
