package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import com.graphinsight.indicator.util.StringUtil;
import lombok.Data;

import javax.persistence.*;
import java.io.*;
import java.util.Date;
import java.util.UUID;

@MappedSuperclass
@Data
public class BaseModel implements Serializable {

    /**
     * ID
     */
    @Id              //主键id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "bigint(10) unsigned COMMENT '唯一主键'")
    protected Long id;

    /**
     * code状态下的唯一标识
     */
    @Column(columnDefinition = "varchar(255) COMMENT 'code状态下的唯一标识'")
    protected String code;

    /**
     * 创建人
     */
    @Column(columnDefinition = "varchar(255) COMMENT '创建人'")
    protected String creator;

    /**
     * 修改人
     */
    @Column(columnDefinition = "varchar(255) COMMENT '修改人'")
    protected String updater;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Column(columnDefinition = "datetime COMMENT '创建时间'")
    protected Date createDate = new Date();

    /**
     * 修改时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Column(columnDefinition = "datetime COMMENT '修改时间'")
    protected Date updateDate = new Date();

    public void initCreate() {

        String userName = UserThreadLocalUtil.getUserName();
        this.creator = userName;
        this.updater = userName;

        this.createDate = new Date();
        this.updateDate = new Date();

        this.code = this.getClass().getSimpleName() + "_" + UUID.randomUUID().toString().replaceAll("-", "");

    }

    public void initUpdate() {

        String userName = UserThreadLocalUtil.getUserName();
        this.updater = userName;
        this.updateDate = new Date();

    }


    protected boolean equalsStr(String str1, String str2){
        if(StringUtil.isEmpty(str1) && StringUtil.isEmpty(str2)){
            return true;
        }
        if(!StringUtil.isEmpty(str1) && str1.equals(str2)){
            return true;
        }
        return false;
    }

    public BaseModel deepClone() throws Exception {
        //序列化
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
        objectOutputStream.writeObject(this);
        //反序列化
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
        BaseModel roomClone = (BaseModel)objectInputStream.readObject();

        return roomClone;

    }

}
