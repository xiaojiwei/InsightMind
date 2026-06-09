package com.graphinsight.indicator.service.impl;

import com.alibaba.druid.util.StringUtils;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.mapper.UploadFileMapper;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.dao.DataSourceDao;
import com.graphinsight.indicator.dao.FolderDao;
import com.graphinsight.indicator.dao.SpaceDao;
import com.graphinsight.indicator.enums.ChartType;
import com.graphinsight.indicator.enums.FileDownStatus;
import com.graphinsight.indicator.enums.LineStatus;
import com.graphinsight.indicator.model.Order;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.*;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.criteria.*;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

@DS("mysql")
@Service
public class DataSourceServiceImpl implements DataSourceService {

    @Autowired
    private ChartQueryService chartQueryService;

    @Autowired
    private FileSourceService fileSourceService;

    @Resource
    protected RedisCacheService redisCacheService;

    @Resource
    protected BosFileService bosFileService;

    @Autowired
    private DataSourceDao dataSourceDao;

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private SpaceDao spaceDao;

    @Autowired
    private DownFileInfoService downFileInfoService;

    @Autowired
    private UploadFileInfoService uploadFileInfoService;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Long getCountByDimCodeAndMeasCode(List<String> codeList) {
        return dataSourceDao.getCountByDimCodeAndMeasCode(codeList);
    }

    @Override
    @Transactional
    public void deleteDataSourceByFolder(Folder folder) {

        Set<Folder> childrenSet = folder.getChildren();
        Set<DataSource> dataSourceSet = folder.getDataSourceSet();

        for (DataSource dataSource : dataSourceSet) {
            this.dataSourceDao.deleteById(dataSource.getId());
        }

        if (!CollectionUtils.isEmpty(childrenSet)) {
            for (Folder childFolder : childrenSet) {
                this.deleteDataSourceByFolder(childFolder);
            }

        }

    }

    @Override
    @Transactional
    public DataSource get(Long id) {
        DataSource dataSource = dataSourceDao.getById(id);
        Space space = dataSource.getSpace();
        if (null != space) {
            dataSource.setSpaceId(space.getId());
        }

        return dataSource;
    }

    @Override
    @Transactional
    public List<DataSource> listByIds(Collection ids) {
        List<DataSource> dataSources = dataSourceDao.findAllById(ids);
        if (!CollectionUtils.isEmpty(dataSources)) {
            dataSources.forEach(dataSource -> {
                Space space = dataSource.getSpace();
                if (null != space) {
                    dataSource.setSpaceId(space.getId());
                }
            });
        }

        return dataSources;
    }


    @Override
    @Transactional
    public boolean delete(Long id) {

        boolean isDel = false;
        boolean exists = this.dataSourceDao.existsById(id);
        if (exists) {
            this.dataSourceDao.deleteById(id);
            isDel = true;
        }

        return isDel;

    }

    /**
     * Build Sql
     *
     * @param hql
     * @return
     */
    private String buildPageSql(final String hql) {

        String pageHql = "select ds " + hql;

        return pageHql;

    }

    private void copyOrder(BaseConfigure baseConfigure, BaseConfigure copyBaseConfig) {

        Order order = baseConfigure.getOrder();
        //防御
        if (null == order) {
            return;
        }

        Order copy = new Order();

        BeanUtils.copyProperties(order, copy, "id", "valueList");

        this.copyValueList(order, copy);

        copyBaseConfig.setOrder(copy);

    }

    private void copyValueList(Order order, Order copyOrder) {

        List<String> valueList = order.getValueList();
        if (CollectionUtils.isEmpty(valueList)) {
            return;
        }

        List<String> copyValueList = new ArrayList<>();
        for (String value : valueList) {
            copyValueList.add(new String(value));
        }

        copyOrder.setValueList(copyValueList);

    }

    private void copyDataList(Operator operator, Operator copyOperator) {
        List<String> dataList = operator.getDataList();
        if (CollectionUtils.isEmpty(dataList)) {
            return;
        }

        List<String> copyDataList = new ArrayList<>();
        for (String data : dataList) {
            copyDataList.add(data);
        }

        copyOperator.setDataList(copyDataList);

    }

    private void copyRatioList(BaseConfigure baseConfigure, BaseConfigure copyBaseConfig) {

        List<Ratio> ratioList = baseConfigure.getRatioList();
        if (CollectionUtils.isEmpty(ratioList)) {
            return;
        }

        List<Ratio> copyRatioList = new LinkedList<>();
        for (Ratio ratio : ratioList) {

            Ratio copyRadio = new Ratio();
            BeanUtils.copyProperties(ratio, copyRadio, "id");

            //copy ratio
            this.copyOperatorList(ratio, copyRadio);

            copyRatioList.add(copyRadio);
        }

        copyBaseConfig.setRatioList(copyRatioList);

    }

    private void copyMeasGroupSet(BaseConfigure baseConfigure, BaseConfigure copyBaseConfig) {

        List<BaseConfigure> measGroupSet = baseConfigure.getMeasGroupSet();
        if (CollectionUtils.isEmpty(measGroupSet)) {
            return;
        }

        List<BaseConfigure> copyMeasGroupSet = new LinkedList<>();
        for (BaseConfigure configure : measGroupSet) {

            BaseConfigure copyConfig = new BaseConfigure();
            BeanUtils.copyProperties(configure, copyConfig, "id", "order", "measGroupSet");

            //copy order
            this.copyOrder(configure, copyConfig);
            //copy measGroupSet
            this.copyMeasGroupSet(configure, copyConfig);

            copyMeasGroupSet.add(copyConfig);
        }

        copyBaseConfig.setMeasGroupSet(copyMeasGroupSet);

    }

    private void copyOperatorList(Ratio ratio, Ratio copyRatio) {

        List<Operator> operatorList = ratio.getOperatorList();

        List<Operator> copyOperatorList = new LinkedList<>();
        for (Operator operator : operatorList) {

            Operator copyOperator = new Operator();
            BeanUtils.copyProperties(operator, copyOperator, "id", "dataList");

            this.copyDataList(operator, copyOperator);
            copyOperatorList.add(copyOperator);

        }

        copyRatio.setOperatorList(copyOperatorList);

    }

    private void copyFilterTreeList(FilterTree filterTree, FilterTree orgCopyFilter) {

        Set<FilterTree> filterTreeSet = filterTree.getFilterTreeSet();

        Set<FilterTree> copyFilterTreeSet = new LinkedHashSet<>();
        for (FilterTree sonFilterTree : filterTreeSet) {

            FilterTree copyFilterTree = new FilterTree();
            BeanUtils.copyProperties(sonFilterTree, copyFilterTree, "id", "filter", "filterTreeSet");

            Filter filter = filterTree.getFilter();
            if (null != filter) {

                Filter copyFilter = new Filter();
                BeanUtils.copyProperties(filter, copyFilter, "id", "operatorList");

                this.copyOperatorList(filter, copyFilter);

                orgCopyFilter.setFilter(copyFilter);
            }

            this.copyFilterTreeList(sonFilterTree, copyFilterTree);
            copyFilterTreeSet.add(copyFilterTree);

        }

        orgCopyFilter.setFilterTreeSet(copyFilterTreeSet);

    }

    private void copyOperatorList(Filter filter, Filter copyFilter) {

        List<Operator> operatorList = filter.getOperatorList();

        List<Operator> copyOperatorList = new LinkedList<>();
        for (Operator operator : operatorList) {

            Operator copyOperator = new Operator();
            BeanUtils.copyProperties(operator, copyOperator, "id", "dataList");

            this.copyDataList(operator, copyOperator);
            copyOperatorList.add(copyOperator);

        }

        copyFilter.setOperatorList(copyOperatorList);

    }

    private String buildName(String name, Integer index) {

        String newName = name + index;
        List<DataSource> dataSourceList = this.dataSourceDao.findAllByName(newName);
        if (!CollectionUtils.isEmpty(dataSourceList)) {
            String dName = this.buildName(name, ++index);
            return dName;
        } else {
            return newName;
        }
    }

    @Override
    public DataSource copyDataSource(Long id) {

        DataSource dataSource = this.dataSourceDao.getById(id);
        DataSource copy = this.copyDS(dataSource);


        return copy;

    }

    @Override
    public Long copy(Long id) {

        DataSource dataSource = this.copyDataSource(id);

        this.dataSourceDao.save(dataSource);

        return dataSource.getId();

    }

    private DataSource copyDS(DataSource dataSource) {

        DataSource copy = new DataSource();
        BeanUtils.copyProperties(dataSource, copy, "id", "code", "configureList", "filterList", "filterTreeList");

        copy.initCreate();
        copy.initUpdate();

        String copyName = this.buildName(dataSource.getName() + "_" + IndicatorConstant.MEASSAGE_COPY, 1);
        copy.setName(copyName);
        copy.setLineStatus(LineStatus.OFF);

        List<BaseConfigure> baseConfigureList = dataSource.getConfigureList();
        if (!CollectionUtils.isEmpty(baseConfigureList)) {
            //copy 配置信息
            List<BaseConfigure> copyBaseConfigureList = new ArrayList<>();
            for (BaseConfigure baseConfigure : baseConfigureList) {

                BaseConfigure copyBaseConfig = new BaseConfigure();
                BeanUtils.copyProperties(baseConfigure, copyBaseConfig, "id", "order", "measGroupSet", "ratioList");

                //copy order
                this.copyOrder(baseConfigure, copyBaseConfig);
                //copy measGroupSet
                this.copyMeasGroupSet(baseConfigure, copyBaseConfig);

                //copy ratioList
                this.copyRatioList(baseConfigure, copyBaseConfig);

                copyBaseConfigureList.add(copyBaseConfig);

            }

            //设置依赖的维度、指标
            copy.setConfigureList(copyBaseConfigureList);
        }

        List<Filter> filterList = dataSource.getFilterList();
        if (!CollectionUtils.isEmpty(filterList)) {
            //copy filter
            List<Filter> copyFilterList = new ArrayList<>();

            for (Filter filter : filterList) {

                Filter copyFilter = new Filter();
                BeanUtils.copyProperties(filter, copyFilter, "id", "operatorList");

                this.copyOperatorList(filter, copyFilter);

                copyFilterList.add(copyFilter);

            }

            //设置过滤器
            copy.setFilterList(copyFilterList);
        }

        List<FilterTree> filterTreeList = dataSource.getFilterTreeList();
        if (!CollectionUtils.isEmpty(filterTreeList)) {
            //copy filter
            List<FilterTree> copyFilterTreeList = new ArrayList<>();

            for (FilterTree filterTree : filterTreeList) {

                FilterTree copyFilterTree = new FilterTree();
                BeanUtils.copyProperties(filterTree, copyFilterTree, "id", "filterTreeSet", "filter");

                this.copyFilterTreeList(filterTree, copyFilterTree);

                copyFilterTreeList.add(copyFilterTree);

            }

            //设置过滤器
            copy.setFilterTreeList(copyFilterTreeList);
        }

        return copy;
    }

    /**
     * Build Sql
     *
     * @param hql
     * @return
     */
    private String buildCountSql(String hql) {

        String cntHql = "select count(distinct ds) " + hql;
        return cntHql;

    }

    /**
     * 文件夹Id
     *
     * @param folderId
     * @return
     */
    private String buildByFolderIdSql(final Long folderId) {

        String hql = "From DataSource as ds left join ds.folder as f where f.id='" + folderId + "'";
        return hql;

    }

    private String buildBaseSql(final SearchText searchText) {

        String text = searchText.getText();
        String enters = ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), text);

        String hql = "From DataSource as ds where 1=1";

        if (!StringUtil.isEmpty(enters)) {
            hql += " and (ds.name like '%" + enters + "%'"
                    + " or ds.creator like '%" + enters + "%'"
                    + " or ds.updater like '%" + enters + "%')";
        }

        Long spaceId = searchText.getSpaceId();
        if (null != spaceId) {

            hql += " and ds.space.id = " + spaceId;

        }

        boolean isMine = searchText.isMine();

        if (isMine) {

            String userName = UserThreadLocalUtil.getUserName();
            hql += " and ds.creator = '" + userName + "'";

        }

        return hql;

    }

    @Override
    public List<DataSource> listAll() {
        List<DataSource> dataSourceList = this.dataSourceDao.findAll();
        return dataSourceList;
    }

    @Override
    public com.graphinsight.indicator.model.Page list(Long folderId) {

        Integer pageNo = Integer.valueOf(0);
        Integer pageSize = Integer.valueOf(999);

        String baseHql = this.buildByFolderIdSql(folderId);

        String hql = this.buildPageSql(baseHql);
        Query query = this.entityManager.createQuery(hql);
        query.setFirstResult(pageNo);
        query.setMaxResults(pageSize);
        //当前页数据
        List list = query.getResultList();

        String cntHql = this.buildCountSql(baseHql);
        Query cntQuery = this.entityManager.createQuery(cntHql);
        Long cnt = (Long) cntQuery.getSingleResult();

        com.graphinsight.indicator.model.Page page = new com.graphinsight.indicator.model.Page();
        page.setContent(list);
        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(cnt.intValue());
        pageInfo.calc();
        pageInfo.calcRange(pageNo);
        page.setPageInfo(pageInfo);

        return page;
    }

    @Override
    public com.graphinsight.indicator.model.Page list(SearchText searchText) {

        Integer pageNo = searchText.getPageNo();
        Integer pageSize = searchText.getPageSize();

        String baseHql = this.buildBaseSql(searchText);

        String hql = this.buildPageSql(baseHql);
        Query query = this.entityManager.createQuery(hql);
//        query.setFirstResult(pageNo);
//        query.setMaxResults(pageSize);
        //当前页数据
        List list = query.getResultList();

//        String cntHql = this.buildCountSql(baseHql);
//        Query cntQuery = this.entityManager.createQuery(cntHql);
//        Long cnt = (Long)cntQuery.getSingleResult();

        com.graphinsight.indicator.model.Page page = new com.graphinsight.indicator.model.Page();
        page.setContent(list);
        /*
        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(cnt.intValue());
        pageInfo.calc();
        pageInfo.calcRange(pageNo);
        page.setPageInfo(pageInfo);
         */

        return page;

    }

    @Override
    public Page<DataSource> list(final DataSource dataSource) {

        Integer pageNo = dataSource.getPageNo();
        Integer pageSize = dataSource.getPageSize();

        PageRequest pageable = PageRequest.of(pageNo, pageSize);

        Page<DataSource> page = dataSourceDao.findAll(new Specification<DataSource>() {
            @Override
            public Predicate toPredicate(Root<DataSource> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {

                List<Predicate> orPredicateList = new ArrayList<Predicate>();//创建一个 or 条件集合
                String searchText = dataSource.getName();

//                获取属性
                Path<String> id = root.get("id");
                Path<String> name = root.get("name");
                Path<String> creator = root.get("creator");
                Path<String> updater = root.get("updater");

//                构造查询条件
                boolean isNumber = StringUtils.isNumber(searchText);
                if (isNumber) {
                    Predicate pId = cb.equal(id, Long.valueOf(searchText));
                    orPredicateList.add(pId);
                }

                Predicate pName = cb.like(name, "%" + searchText + "%");
                Predicate pCreator = cb.like(creator, "%" + searchText + "%");
                Predicate pUpdater = cb.like(updater, "%" + searchText + "%");

                orPredicateList.add(pName);
                orPredicateList.add(pCreator);
                orPredicateList.add(pUpdater);

                //必须使用toArray(T[])的有参数方法，因为cq.where(p)中的参数的类型必须是Predicate[]数组类型。
                //toArray()无参返回的是一个Object类型。
                //新建数组方式之一：new A[number]
                Predicate searchPermission = cb.or(orPredicateList.toArray(new Predicate[orPredicateList.size()]));

                String loginUserName = UserThreadLocalUtil.getUserName();

                List<Predicate> userPredicateList = new ArrayList<Predicate>();//创建一个 and 条件集合
                Path<String> creatorName = root.get("creator");
                Predicate userName = cb.equal(creatorName, loginUserName);
                userPredicateList.add(userName);

                Predicate andPermission = cb.and(userPredicateList.toArray(new Predicate[userPredicateList.size()]));

                return cq.where(searchPermission, andPermission).getRestriction();

            }

        }, pageable);

        return page;
    }

    @Override
    @Transactional
    public Long saveByNameAndFolderId(DataSource dataSource) {

        DataSource beforDataSource = this.dataSourceDao.getById(dataSource.getId());
        beforDataSource.initUpdate();
        beforDataSource.setName(dataSource.getName());

        Folder folder = dataSource.getFolder();
        if (null != folder) {
            Folder sessionFolder = folderDao.getById(folder.getId());
            sessionFolder.getDataSourceSet().add(beforDataSource);
            beforDataSource.setFolder(sessionFolder);
        }

        DataSource ds = dataSourceDao.save(beforDataSource);

        return ds.getId();

    }


    private void setIdNull(Collection<BaseModel> baseModelCollection) {
        for (BaseModel baseModel : baseModelCollection) {
            this.setIdNull(baseModel);
        }
    }

    private void setIdNull(BaseModel baseMode) {
        baseMode.setId(null);
    }

    @Override
    @Transactional
    public Long save(DataSource paramDataSource) {

        DataSource dataSource = null;
        if (paramDataSource.getId() == null) {
            dataSource = paramDataSource;
            dataSource.initCreate();
        } else {

            dataSource = this.dataSourceDao.getById(paramDataSource.getId());

            //使用copy对象可以去掉原始id。
            dataSource.setName(paramDataSource.getName());

            dataSource.setTotalSub(paramDataSource.isTotalSub());
            dataSource.setTotalSum(paramDataSource.isTotalSum());

            dataSource.initUpdate();

        }

        DataSource copyDs = this.copyDS(paramDataSource);

        dataSource.getConfigureList().clear();
        dataSource.getFilterList().clear();

        dataSource.getConfigureList().addAll(copyDs.getConfigureList());
        dataSource.getFilterList().addAll(copyDs.getFilterList());

        Folder folder = paramDataSource.getFolder();
        if (null != folder) {
            Folder sessionFolder = folderDao.getById(folder.getId());
            sessionFolder.getDataSourceSet().add(dataSource);
            dataSource.setFolder(sessionFolder);
        }

        Long spaceId = paramDataSource.getSpaceId();
        if (null != spaceId) {
            Space sessionSpace = this.spaceDao.getById(spaceId);
            sessionSpace.getDataSourceSet().add(dataSource);
            dataSource.setSpace(sessionSpace);
        }

        DataSource ds = dataSourceDao.save(dataSource);

        return ds.getId();

    }

    @Override
    public FileDownInfo getFileStatus(String downloadId) {

        FileDownInfo fileDownInfo = redisCacheService.get(downloadId, FileDownInfo.class);
        fileDownInfo.setChoiceMeasureSet(null);
        fileDownInfo.setChoiceDimensionSet(null);
        if (FileDownStatus.COMPLETE.equals(fileDownInfo.getFileDownStatus())) {
            // 记录fileKey信息
            uploadFileInfoService.save(fileDownInfo);
        }

        return fileDownInfo;

    }

    @Override
    public void exportBos(String downloadid, HttpServletResponse response) {

        try {

            FileDownInfo fileDownInfo = redisCacheService.get(downloadid, FileDownInfo.class);
            if (FileDownStatus.COMPLETE.equals(fileDownInfo.getFileDownStatus())) {
                try {

                    DownFileInfo downFileInfo = new DownFileInfo();
                    downFileInfo.setUserInfo(UserThreadLocalUtil.getUserName());
                    downFileInfo.setFilePath(fileDownInfo.getFileName());
                    if (fileDownInfo.getSql().length() > 7000) {
                        downFileInfo.setSqlText(fileDownInfo.getSql().substring(0, 7000));
                    } else {
                        downFileInfo.setSqlText(fileDownInfo.getSql());
                    }

                    downFileInfo.setCount(fileDownInfo.getCount().longValue());
                    downFileInfo.initCreate();

                    downFileInfoService.save(downFileInfo);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                String fileName = fileDownInfo.getFileName();

                bosFileService.downloadBosFile(response, fileName, downloadid);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void downFile(String downloadId, HttpServletResponse response) {

        FileDownInfo fileDownInfo = this.redisCacheService.get(downloadId, FileDownInfo.class);
        if (null == fileDownInfo || !FileDownStatus.COMPLETE.equals(fileDownInfo.getFileDownStatus())) {
            throw new RuntimeException("file invalid.");
        }

        String fileName = fileDownInfo.getFileName();
        File file = new File(fileName);
        //文件名
        // 清空缓冲区，状态码和响应头(headers)
        response.reset();
        // 设置ContentType，响应内容为二进制数据流，编码为utf-8，此处设定的编码是文件内容的编码
        response.setContentType("application/octet-stream;charset=utf-8");
        // 以（Content-Disposition: attachment; filename="filename.jpg"）格式设定默认文件名，设定utf编码，此处的编码是文件名的编码，使能正确显示中文文件名
        try {
            response.setHeader("Content-Disposition", "attachment;fileName=" + fileName + ";filename*=utf-8''" + URLEncoder.encode(fileName, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // 实现文件下载
        byte[] buffer = new byte[1024];
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        try {

            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            // 获取字节流
            OutputStream os = response.getOutputStream();
            int i = bis.read(buffer);
            while (i != -1) {
                os.write(buffer, 0, i);
                i = bis.read(buffer);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        File delFile = new File(fileName);
        if (delFile.exists()) {
            delFile.delete();
        }

        fileDownInfo.setFileDownStatus(FileDownStatus.INVALID);
        this.redisCacheService.put(downloadId, fileDownInfo);

    }

    @Override
    public void exportExcel(DataSource dataSource, HttpServletResponse response) throws IOException {

        dataSource.setChartType(ChartType.HIST);
        PageData pageData = this.chartQueryService.execQuery(dataSource);
        QueryResult result = this.formatQueryResult(pageData.getCellList());

        fileSourceService.writeSheet(dataSource, result, response);

    }

    @Override
    public String exportFile(DataSource dataSource) {

//        dataSource.setChartType(ChartType.SYNCFILE);
        dataSource.setDownFile(true);
        PageData pageData = this.chartQueryService.execQuery(dataSource);
        QueryResult result = new QueryResult();
        result.setDownloadId(pageData.getDownloadId());

        return result.getDownloadId();

    }

    public QueryResult formatQueryResult(List<List<Cell>> cellList) {

        List<QueryResultColumnInfo> columnInfoList = new ArrayList<QueryResultColumnInfo>();
        List<List<String>> values = new ArrayList<>();
        for (int idx = 0; idx < cellList.size(); idx++) {
            List<String> rowValues = new ArrayList<>();
            List<Cell> cellVOList = cellList.get(idx);
            for (Cell cell : cellVOList) {
                if (idx == 0) {
                    QueryResultColumnInfo info = new QueryResultColumnInfo();
                    info.setName(cell.getName());
                    info.setType(cell.getType());
                    info.setDimType(cell.getDimType());
                    columnInfoList.add(info);
                }
                rowValues.add(cell.getData());
            }
            values.add(rowValues);
        }

        QueryResult result = new QueryResult();
        result.setInfos(columnInfoList);
        result.setValues(values);
        return result;
    }

}
