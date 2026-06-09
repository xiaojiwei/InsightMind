package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;


@Data
public class AiGptVo {


    private String msg;
    private int code;
    private DataInfo data;
    private boolean success;

    @Data
    public static class DataInfo {

        private List<ChoicesInfo> choices;
    }

    @Data
    public static class ChoicesInfo {

        private ContentFilterResults content_filter_results;
        private String role;
        private String finishReason;
        private int index;
        private String content;
    }
    @Data
    public static class ContentFilterResults {

        private SelfHarm self_harm;
        private Hate hate;
        private Sexual sexual;
        private Violence violence;
    }
    @Data
    public static class Violence {

        private String severity;
        private boolean filtered;
    }
    public  static class Sexual {

        private String severity;
        private boolean filtered;
    }

    public static class Hate {

        private String severity;
        private boolean filtered;
    }

    public static class SelfHarm {

        private String severity;
        private boolean filtered;
    }
}
