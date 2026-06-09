package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Table;
import javax.persistence.*;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "folder", comment="文件夹")
public class Folder extends BaseModel {

    /**
     * 文件夹名
     */
    @Column(columnDefinition = "varchar(255) COMMENT '文件夹名'")
    private String name;

    /**
     * 父文件夹
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"childSet", "dataSourceSet"})
    @Cascade({org.hibernate.annotations.CascadeType.SAVE_UPDATE})
    @JoinColumn(name="parent_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Folder parent;

    /**
     * 下级文件夹
     */
    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy="parent")
    @JsonIgnoreProperties({"parent"})
    @Cascade({org.hibernate.annotations.CascadeType.SAVE_UPDATE})
    @OrderBy(value = "createDate desc")
    private Set<Folder> children = new LinkedHashSet<>();

    /**
     * 数据源
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.REFRESH})
    @JsonIncludeProperties({"name", "id", "chartType", "lineStatus", "creator", "createDate", "updater", "updateDate"})
    @JoinColumn(name="folder_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "createDate desc")
    private Set<DataSource> dataSourceSet = new LinkedHashSet<>();

    /**
     * 该文件夹下数据源
     */
    @Column(columnDefinition = "int(11) COMMENT '该文件夹下数据源'")
    private Integer dataSourceTotal;

    /**
     * 该文件夹下所有子文件夹
     */
    @Column(columnDefinition = "int(11) COMMENT '该文件夹下所有子文件夹'")
    private Integer folderTotal;

    @Transient
    private Long spaceId;

    /**
     * 工作空间id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIncludeProperties({"name", "id"})
    @Cascade({org.hibernate.annotations.CascadeType.SAVE_UPDATE})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Space space;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Folder folder = (Folder) o;
        return Objects.equals(name, folder.name) &&
                Objects.equals(parent, folder.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, parent);
    }

    @Override
    public String toString() {
        return "Folder{" +
                "name='" + name + '\'' +
                '}';
    }

}
