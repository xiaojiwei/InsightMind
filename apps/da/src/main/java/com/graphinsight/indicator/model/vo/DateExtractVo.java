package com.graphinsight.indicator.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

//@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DateExtractVo {
    private boolean success;
    private List<DateInfo> data;


    @Data
    public static class DateInfo {
        private String text;
        private List<Integer> offset;
        private String type;
        private Detail detail;
        private List<Date> dateTime = new ArrayList<>();
    }

    @Data
    public static class Detail {

        private String type;
        private String definition;
        private Object time;
    }

}
