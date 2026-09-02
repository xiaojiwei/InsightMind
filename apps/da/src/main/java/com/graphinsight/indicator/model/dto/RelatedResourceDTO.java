package com.graphinsight.indicator.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatedResourceDTO {

    private Integer id;

    private Integer type;

    private String typeName;

    private String name;

    private Long resourceId;

    private Long spaceId;

    private String spaceName;

    private String creator;

    private LocalDateTime createDate;
}
