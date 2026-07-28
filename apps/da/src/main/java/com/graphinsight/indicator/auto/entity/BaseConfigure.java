package com.graphinsight.indicator.auto.entity;

import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 
 * </p>
 *
 * @since 2022-03-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BaseConfigure implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String creator;

    //private LocalDateTime creatorDate;

    private LocalDateTime updateDate;

    private String updater;

    private String aggFun;

    private String alias;

    private Integer axisType;

    private String vColumn;

    private Integer dimType;

    private Boolean hasSubtotal;

    private Integer vIndex;

    private Integer measureType;

    private String name;

    private Integer ordinal;

    private String subtotalAlias;

    private Integer viewType;

    private Long orderId;

    private Long valueFormatId;

    private Long dataSourceId;

    private Long measGroupId;

    private LocalDateTime createDate;


}
