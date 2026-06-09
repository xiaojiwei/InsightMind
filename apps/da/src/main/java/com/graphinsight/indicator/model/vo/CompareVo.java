package com.graphinsight.indicator.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

//@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompareVo {
    private boolean success;
    private List<List<String>> data;
    private List<String> tok;
    private Object con;

}
