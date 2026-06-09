package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.FileDownInfo;
import com.graphinsight.indicator.model.Folder;
import com.graphinsight.indicator.model.SearchText;
import org.springframework.data.domain.Page;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * 数据源服务接口
 */
public interface DataSourceService {

    /**
     * 同步导出xls文件
     * @param data
     * @param response
     */
    void exportExcel(DataSource data, HttpServletResponse response) throws IOException;

    /**
     * 异步导出文件
     * @param data
     * @return 下载文件标识
     */
    String exportFile(DataSource data);

    /**
     * 下载已经生成的文件
     * @param downloadId
     * @param response
     * @return
     */
    void downFile(String downloadId, HttpServletResponse response);

    /**
     * 获取文件状态
     * @param downloadId
     * @return
     */
    FileDownInfo getFileStatus(String downloadId);

    /**
     * 同步导出xls文件
     * @param downloadId
     * @param response
     */
    void exportBos(String downloadId, HttpServletResponse response) throws Exception;

    /**
     * 复制数据源
     * @param id
     */
    Long copy(Long id);

    /**
     * 数据源持久化
     * @param dataSource
     * @return
     */
    Long save(DataSource dataSource);

    /**
     * 查看数据源
     * @param id
     * @return
     */
    DataSource get(Long id);

    /**
     * 删除数据源
     * @param id
     * @return
     */
    boolean delete(Long id);

    Page<DataSource> list(DataSource dataSource);

    /**
     * 查询分页
     * @param searchText
     * @return
     */
    com.graphinsight.indicator.model.Page list(SearchText searchText);


    /**
     * 查询分页
     * @param folderId
     * @return
     */
    com.graphinsight.indicator.model.Page list(Long folderId);

    /**
     * 查询分页
     * @return
     */
    List<DataSource> listAll();

    /**
     * 删除数据源
     * @param folder
     */
    void deleteDataSourceByFolder(Folder folder);

    /**
     * 修改时只更新name folder
     * @param dataSource
     * @return
     */
    Long saveByNameAndFolderId(DataSource dataSource);

    /**
     * 查询包含（维度|指标）code的数据源
     * @param codeList
     * @return
     */
    Long getCountByDimCodeAndMeasCode(List<String> codeList);

    DataSource copyDataSource(Long id);


    /**
     * 根据id批量获取数据集
     * @param ids
     * @return
     */
    List<DataSource> listByIds(Collection ids);

}
