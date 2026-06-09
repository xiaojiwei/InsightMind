package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.dao.FolderDao;
import com.graphinsight.indicator.dao.SpaceDao;
import com.graphinsight.indicator.enums.FolderDataSourceType;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.vo.BaseVO;
import com.graphinsight.indicator.model.vo.FolderDataSourceVO;
import com.graphinsight.indicator.service.DataSourceService;
import com.graphinsight.indicator.service.FolderService;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.codehaus.jackson.map.Serializers;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.*;

@DS("mysql")
@Service
public class FolderServiceImpl implements FolderService {

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private SpaceDao spaceDao;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private DataSourceService dataSourceService;

    @Override
    public Page listFolderPage(SearchText searchText) {

        Page allPage = new Page();
        Page folderPage = this.list(searchText);
        Page dataSourcePage = this.dataSourceService.list(searchText);

        List allContent = new LinkedList();
        List<Folder> folderList = folderPage.getContent();
        List<DataSource> dataSourceList = dataSourcePage.getContent();

        for (Folder folder : folderList) {

            this.initFolder(folder, dataSourceList);
            Folder parentFolder = folder.getParent();
            if (null == parentFolder) {
                allContent.add(folder);
            }

        }

        //按前端需要的格式转换
        allContent = this.format(allContent, folderList, dataSourceList, false);

        allPage.setContent(allContent);

        return allPage;

    }

    private Folder getRootFolder(Folder folder) {
        Folder parentFolder = folder.getParent();
        if (null == parentFolder) {
            return folder;
        } else {
            return this.getRootFolder(parentFolder);
        }
    }

    private boolean hasContains(List<Folder> allContent, Folder rootFolder) {
        boolean exist = false;
        for (Folder folder : allContent) {
            if (folder.getId().equals(rootFolder.getId())) {
                exist = true;
                break;
            }
        }
        return exist;
    }

    @Override
    public Page allList(SearchText searchText) {

        Page allPage = new Page();
        Page folderPage = this.list(searchText);
        Page dataSourcePage = this.dataSourceService.list(searchText);

        List allContent = new LinkedList();
        List<Folder> folderList = folderPage.getContent();
        List<DataSource> dataSourceList = dataSourcePage.getContent();

        for (Folder folder : folderList) {

            this.initFolder(folder, dataSourceList);

            Folder rootFolder = getRootFolder(folder);
            if (!this.hasContains(allContent, rootFolder)) {
                allContent.add(rootFolder);
            }

        }

        //按前端需要的格式转换
        allContent = this.format(allContent, folderList, dataSourceList);

        //将无文件夹的数据源放入结果中
        for (DataSource dataSource : dataSourceList) {
            Folder folder = dataSource.getFolder();
            if (null == folder) {

                FolderDataSourceVO dsVO = this.transform(dataSource);
                allContent.add(dsVO);

            }
        }

        this.sortByUpdateTime(allContent);

        allPage.setContent(allContent);

        return allPage;

    }

    private List sortByUpdateTime(List allContent) {

        allContent.sort(new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {

                FolderDataSourceVO vo1 = (FolderDataSourceVO) o1;
                FolderDataSourceVO vo2 = (FolderDataSourceVO) o2;

                Long tvo1 = vo1.getSortData().getTime();
                Long tvo2 = vo2.getSortData().getTime();

                return tvo1 < tvo2 ? 1 : -1;
            }
        });

        for (Object o : allContent) {

            FolderDataSourceVO vo = (FolderDataSourceVO) o;
            List<FolderDataSourceVO> childrenList = vo.getChildren();
            if (!CollectionUtils.isEmpty(childrenList)) {
                this.sortByUpdateTime(childrenList);
            }

        }

        return allContent;

    }

    private FolderDataSourceVO transform(DataSource dataSource) {

        FolderDataSourceVO dsVO = new FolderDataSourceVO();

        dsVO.setId(dataSource.getId());
        dsVO.setCode(dataSource.getCode());
        dsVO.setName(dataSource.getName());
        dsVO.setChartType(dataSource.getChartType());
        dsVO.setNodeType(FolderDataSourceType.DATA_SOURCE);
        dsVO.setLineStatus(dataSource.getLineStatus());
        dsVO.setCreator(dataSource.getCreator());
        dsVO.setCreateDate(dataSource.getCreateDate());
        dsVO.setUpdater(dataSource.getUpdater());
        dsVO.setUpdateDate(dataSource.getUpdateDate());
        dsVO.setSortData(dataSource.getUpdateDate());

        return dsVO;

    }

    private FolderDataSourceVO transform(Folder folder, List<Folder> folderList, List<DataSource> searchFolderSourceList, boolean hasDataSource) {

        FolderDataSourceVO folderVO = new FolderDataSourceVO();

        folderVO.setId(folder.getId());
        folderVO.setName(folder.getName());
        folderVO.setCode(folder.getCode());
        folderVO.setNodeType(FolderDataSourceType.FOLDER);
        folderVO.setCreator(folder.getCreator());
        folderVO.setCreateDate(folder.getCreateDate());
        folderVO.setUpdater(folder.getUpdater());
        folderVO.setUpdateDate(folder.getUpdateDate());
        folderVO.setSortData(folder.getUpdateDate());
        List<FolderDataSourceVO> childrenSet = new LinkedList<>();
        Set<Folder> children = folder.getChildren();

        if (!CollectionUtils.isEmpty(children)) {
            for (Folder childFolder : children) {

                if (null == folderList || this.isSelfOrParent(childFolder, folderList)) {
                    FolderDataSourceVO childVO = this.transform(childFolder, folderList, searchFolderSourceList, hasDataSource);
                    if (childVO.getSortData().getTime() > folderVO.getSortData().getTime()) {
                        //排序使用
                        folderVO.setSortData(childVO.getSortData());
                    }
                    childrenSet.add(childVO);
                }
            }
        }

        if (hasDataSource) {

            Set<DataSource> dataSourceSet = folder.getDataSourceSet();
            if (!CollectionUtils.isEmpty(dataSourceSet)) {
                for (DataSource dataSource : dataSourceSet) {

                    if (null == searchFolderSourceList || searchFolderSourceList.contains(dataSource)) {
                        FolderDataSourceVO dsMap = this.transform(dataSource);
                        if (dsMap.getSortData().getTime() > folderVO.getSortData().getTime()) {
                            //排序使用
                            folderVO.setSortData(dsMap.getSortData());
                        }
                        childrenSet.add(dsMap);
                    }

                }
            }

        }

        folderVO.setChildren(childrenSet);

        return folderVO;

    }

    private List format(List allContent, List<Folder> folderList, List<DataSource> dataSourceList) {
        return this.format(allContent, folderList, dataSourceList, true);
    }

    private boolean isParent(Folder parent, Folder folder) {

        boolean isParent = false;

        Folder selfParentFolder = folder.getParent();
        if (null != selfParentFolder && parent.getId().equals(selfParentFolder.getId())) {
            isParent = true;
        } else if (null != selfParentFolder) {
            isParent = this.isParent(parent, selfParentFolder);
        }

        return isParent;

    }

    private boolean isSelfOrParent(Folder folder, List<Folder> folderList) {

        boolean isParent = false;
        for (Folder folder1 : folderList) {

            //本身
            if (folder.getId().equals(folder1.getId())) {
                isParent = true;
            } else if (this.isParent(folder, folder1)) {
                isParent = true;
            }

        }

        return isParent;

    }

    private List format(List allContent, List<Folder> folderList, List<DataSource> dataSourceList, boolean hasDataSource) {

        List formatContent = new LinkedList();

        for (Object obj : allContent) {
            if (obj instanceof Folder) {

                Folder folder = (Folder) obj;

                FolderDataSourceVO folderMap = this.transform(folder, folderList, dataSourceList, hasDataSource);
                formatContent.add(folderMap);

            }
        }

        return formatContent;

    }

    private void initFolder(Folder folder, List<DataSource> dataSourceList) {

        Set<DataSource> searchSet = new LinkedHashSet<>();
        Set<DataSource> dataSourceSet = folder.getDataSourceSet();
        for (DataSource dataSource : dataSourceSet) {

            //过滤掉不含筛选项的数据集
            if (dataSourceList.contains(dataSource)) {
                searchSet.add(dataSource);
            }

        }

        //处理下级文件夹
        folder.setDataSourceSet(searchSet);
        Set<Folder> childSet = folder.getChildren();
        for (Folder childFolder : childSet) {
            this.initFolder(childFolder, dataSourceList);
        }

    }

    @Override
    @Transactional
    public Page getChild(Long id) {

        Folder folder = this.get(id);

        Page page = new Page();

        List<FolderDataSourceVO> folderList = new LinkedList<>();
        FolderDataSourceVO folderDataSourceVO = this.transform(folder, null, null, true);
        folderList.add(folderDataSourceVO);
        page.setContent(folderList);

        return page;

    }

    @Override
    @Transactional
    public Folder get(Long id) {
        Folder folder = folderDao.getById(id);
        this.initTotal(folder);
        return folder;
    }

    private void initTotal(Folder folder) {

        Set<Folder> childFolderSet = folder.getChildren();
        Integer folderTotal = childFolderSet.size();

        Set<DataSource> dataSourceSet = folder.getDataSourceSet();
        Integer dataSourceTotal = dataSourceSet.size();

        if (!CollectionUtils.isEmpty(childFolderSet)) {

            for (Folder childFolder : childFolderSet) {

                this.initTotal(childFolder);

                folderTotal += childFolder.getFolderTotal();
                dataSourceTotal += childFolder.getDataSourceTotal();

            }

        }

        folder.setFolderTotal(folderTotal);
        folder.setDataSourceTotal(dataSourceTotal);

    }

    @Override
    @Transactional
    public boolean delete(Long id) {

        boolean isDel = false;
        boolean exists = this.folderDao.existsById(id);
        if (exists) {
            Folder folder = this.folderDao.getById(id);
            //删除文件夹下的数据源
            this.dataSourceService.deleteDataSourceByFolder(folder);
            this.folderDao.deleteById(id);
            isDel = true;
        }

        return isDel;

    }

    /**
     * Build Sql
     * @param searchText
     * @return
     */
    private String buildBaseSql(final SearchText searchText) {

        String text = searchText.getText();
        Long spaceId = searchText.getSpaceId();
        String enters = ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), text);
        String hql = "select distinct f From Folder as f "
                + " left join f.dataSourceSet as ds ";

        if (null != spaceId) {
            hql += " left join f.space as s ";
        }

        hql += " where 1=1 ";

        if (null != spaceId) {
            hql += " and s.id='" + spaceId + "'";
        }

        if (!StringUtil.isEmpty(text)) {

            hql += " and (f.name like '%" + enters + "%'"
                    + " or f.creator like '%" + enters + "%'"
                    + " or f.updater like '%" + enters + "%'"

                    + " or ds.name like '%" + enters + "%'"
                    + " or ds.creator like '%" + enters + "%'"
                    + " or ds.updater like '%" + enters + "%')";

        }

        boolean isMine = searchText.isMine();

        if (isMine) {

            String userName = UserThreadLocalUtil.getUserName();
            hql +=  " and (f.creator = '" + userName + "'"
                    + " or ds.creator = '" + userName + "')";

        }

        hql += " order by f.createDate desc";

        return hql;

    }

    /**
     * Build Sql
     * @param hql
     * @return
     */
    private String buildPageSql(final String hql) {

        String pageHql = "select f " + hql;

        return pageHql;

    }

    /**
     * Build Sql
     * @param hql
     * @return
     */
    private String buildCountSql(String hql) {

        String cntHql = "select count(distinct f) " + hql;
        return cntHql;

    }

    @Override
    @Transactional
    public Page list(final SearchText searchText) {

        Integer pageNo = searchText.getPageNo();
        Integer pageSize = searchText.getPageSize();
//        Integer pageNo = 0;
//        Integer pageSize = 9999;

        String hql = this.buildBaseSql(searchText);
        Query query = this.entityManager.createQuery(hql);
//        query.setFirstResult(pageNo);
//        query.setMaxResults(pageSize);
        //当前页数据
        List list = query.getResultList();

//        String cntHql = this.buildCountSql(baseHql);
//        Query cntQuery = this.entityManager.createQuery(cntHql);
//        Long cnt = (Long)cntQuery.getSingleResult();

        Page page = new Page();
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
    @Transactional
    public Long save(Folder folder) {

        Folder beforFolder = null;
        if (folder.getId() == null) {
            beforFolder = folder;
            beforFolder.initCreate();
        } else {
            beforFolder = this.folderDao.getById(folder.getId());
            beforFolder.initUpdate();
        }

        Folder parent = folder.getParent();
        if (null != parent) {
            parent = folderDao.getById(parent.getId());

            Long parentId = parent.getId();
            Long id = beforFolder.getId();
            if (parentId.equals(id)) {
                throw new RuntimeException("The parent object cannot be itself");
            }

            beforFolder.setParent(parent);
            parent.getChildren().add(beforFolder);

        }

        Long spaceId = folder.getSpaceId();
        if (null != spaceId) {
            Space sessionSpace = this.spaceDao.getById(spaceId);
            sessionSpace.getFolderSet().add(beforFolder);
            beforFolder.setSpace(sessionSpace);
        }

        beforFolder.setName(folder.getName());
        Folder folder1 = folderDao.save(beforFolder);

        return folder1.getId();

    }

}
