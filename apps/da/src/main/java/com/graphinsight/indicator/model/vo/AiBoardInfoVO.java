package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import lombok.Getter;
import lombok.Setter;

/**
 * @author houfenglei
 */
@Getter
@Setter
public class AiBoardInfoVO {

    /**
    * 唯一主键
    */
    private Long id;

    /**
    * 帆软地址
    */
    private String boardUrl;

    /**
    * 是否删除 0否 1是
    */
    private String isDel;

    /**
    * 创建时间
    */
    private String createDate;

    /**
    * 创建人
    */
    private String creator;

    /**
    * 修改时间
    */
    private String updateDate;
    private String boardName;

    /**
    * 修改人
    */
    private String updater;

    private User updaterUser;

}
