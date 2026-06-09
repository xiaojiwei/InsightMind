package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.Folder;
import com.graphinsight.indicator.model.Page;
import com.graphinsight.indicator.model.SearchText;

/**
 * 文件夹服务接口
 */
public interface FolderService {

    /**
     * 数据源持久化
     * @param folder
     * @return
     */
    Long save(Folder folder);

    /**
     * 查看数据源
     * @param id
     * @return
     */
    Folder get(Long id);


    /**
     * 获取文件下子集
     * @param id
     * @return
     */
    Page getChild(Long id);

    /**
     * 删除数据源
     * @param id
     * @return
     */
    boolean delete(Long id);

    Page list(SearchText searchText);

    /**
     *
     * @param searchText
     * @return
     */
    Page allList(SearchText searchText);

    /**
     *
     * @param searchText
     * @return
     */
    Page listFolderPage(SearchText searchText);

}
