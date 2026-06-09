package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSONObject;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.graphinsight.indicator.auto.entity.OperateAuthBroad;
import com.graphinsight.indicator.auto.entity.OperateGrantConfig;
import com.graphinsight.indicator.auto.entity.Organization;
import com.graphinsight.indicator.auto.service.IOperateGrantConfigService;
import com.graphinsight.indicator.enums.DBDataSourceType;
import com.graphinsight.indicator.enums.EmployeeOrgType;
import com.graphinsight.indicator.enums.OperateGrantType;
import com.graphinsight.indicator.enums.OrganizationType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.dto.OperateGrantValue;
import com.graphinsight.indicator.model.vo.IndicatorOperateTree;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/5/26
 * Desc:
 */
@Slf4j
@Service
public class UserGrantContextManager {

    @Autowired
    OrganizationManager organizationManager;

    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate dorisJdbcTemplate;

    @Autowired
    @Qualifier("mysqlJdbcTemplate")
    private JdbcTemplate mysqlJdbcTemplate;

    @Autowired
    IOperateGrantConfigService operateGrantConfigService;

    private static Cache<Object, Object> MEM_CACHE = CacheBuilder.newBuilder()
            .initialCapacity(10000)
            .concurrencyLevel(20)
            .expireAfterWrite(3, TimeUnit.HOURS)
            .build();

    public OperateGrantValue getOperateGrantValue(String username, Long grantConfigId) {

        String userKey = username + "_" + grantConfigId;
        OperateGrantValue res = null;
        Object grantValue = MEM_CACHE.getIfPresent(userKey);
        //grantValue = null;
        if (null == grantValue) {

            OperateGrantConfig config = operateGrantConfigService.getById(grantConfigId);
            if (Objects.isNull(config)) {
                throw IndicatorParamNotValidException.error("grantConfigId不存在：" + grantConfigId);
            }
            if (Objects.equals(OperateGrantType.EXECT.getCode(),config.getGrantType())){
                res = exactGrantValueQuery(username, config);
                MEM_CACHE.put(userKey, res);
                return res;
            }

            if (Objects.equals(OperateGrantType.ORG.getCode(),config.getGrantType())){

                res = orgGrantValueQuery(username, config);
                try {
                    operateAuthBoard(username, res, config);
                }catch (Exception e){
                    log.info("行列权限范围扩充异常，username：{}",username,e);
                }
                MEM_CACHE.put(userKey, res);
                return res;
            }

        } else {
            return (OperateGrantValue) grantValue;
        }
        throw IndicatorParamNotValidException.error("授权类型grantType不合法,configId:" + grantConfigId);
    }


    @Value("${pbHost}")
    private String pbHost;

    private void operateAuthBoard(String username,OperateGrantValue value,OperateGrantConfig config){
        List<OperateGrantConfig> configs = operateGrantConfigService.list();
        Set<Long> broadConfigIds = configs.stream().filter(e -> e.getName().contains("范围扩大")).map(e -> e.getId()).collect(Collectors.toSet());

        if (!broadConfigIds.contains(config.getId())) return;

        List<OperateAuthBroad> authBroads = new OperateAuthBroad().selectAll();
        HashMap<String,Integer> positionDeptMap = new HashMap<>();
        authBroads.forEach(e->positionDeptMap.put(e.getPositionCode(),e.getDeptType()));
        String positionCode = getEmployeePosition(username);
        if (!positionDeptMap.containsKey(positionCode)) return;

        List<IndicatorOperateTree> trees = value.getOrgTree();
        List<IndicatorOperateTree> newTrees = new LinkedList<>();

        HashMap<String, OperateAuthBroad> positionMap = new HashMap<>();
        authBroads.forEach(e->positionMap.put(e.getPositionCode(),e));
        OperateAuthBroad operateAuthBroad = positionMap.get(positionCode);
        for (IndicatorOperateTree tree : trees) {
            Map<String,Organization> superiorOrgs = new HashMap<>();
            Set<Integer> superiorDeptTypes = new HashSet<>();
            EmployeeOrgType employeeOrgType = EmployeeOrgType.findByInt(config.getOrgType()).orElse(null);
            OrganizationType organizationType = EmployeeOrgType.getOrganizationType(employeeOrgType);
            organizationManager.listSuperiorOrg(tree.getCode(),organizationType).forEach(e->{
                superiorOrgs.put(e.getOrgCode(),e);
                superiorDeptTypes.add(e.getDeptType());
            });

            String currentOrgCode = tree.getCode();
            Organization currentOrg = superiorOrgs.get(currentOrgCode);
            Integer currentOrgDeptType = currentOrg.getDeptType();
            Integer topDeptType = operateAuthBroad.getDeptType();
            Integer level = operateAuthBroad.getLevel();

            if (superiorDeptTypes.contains(topDeptType)){
                while (level > 0 && !Objects.equals(currentOrgDeptType, topDeptType)) {
                    level--;
                    currentOrg = superiorOrgs.get(currentOrg.getParentCode());
                    currentOrgDeptType = currentOrg.getDeptType();
                }
            }

            IndicatorOperateTree newTree = organizationManager.getIndicatorOperateTree(currentOrg, config);
            newTrees.add(newTree);
        }

        Set<String> keys = value.getKeys();
        Map<String,Object> kvMap = value.getKvMap();
        keys.clear();
        kvMap.clear();
        newTrees.stream().forEach(tree -> reLoadKey(tree,keys,kvMap));
        value.setOrgTree(newTrees);
    }

    public void reLoadKey(IndicatorOperateTree tree,Set<String> keys,Map<String,Object> kvMap){
        keys.add(tree.getCode());
        kvMap.put(tree.getCode(),tree.getName());
        if (tree.getChildren()!=null) tree.getChildren().stream().forEach(e -> reLoadKey(e,keys,kvMap));
    }

    public String getEmployeePosition(String username){
        String res = null;
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<JSONObject> exchange = restTemplate.exchange(pbHost + "/api/v3" + "/roles/position/"+username+"/get", HttpMethod.GET,new HttpEntity<>(new HttpHeaders()),JSONObject.class);
            if (exchange.getStatusCode().value()!=200) return res;
            JSONObject jsonObject = exchange.getBody();
            return jsonObject.getString("positionCode");
        }catch (Exception e){
            log.error("获取用户岗位异常，username：{}",username,e);
        }
        return res;
    }





    private JdbcTemplate getJdbcTemplate(Integer dataSource){
        if (DBDataSourceType.MYSQL.getCode().equals(dataSource)){
            return mysqlJdbcTemplate;
        } else {
            return dorisJdbcTemplate;
        }
    }


    private String generateSql(OperateGrantConfig config){
        String schemaName = config.getSchemaName();
        String tableName = config.getTableName();
        String grantColumnKey = config.getGrantColumnKey();
        String grantColumnValue = config.getGrantColumnValue();
        String selectCols = grantColumnKey + ", "  + grantColumnValue;
        String sql = "select " + selectCols +  " from " + schemaName + "." + tableName + " where `username` = ? ";
        return sql;
    }

    /**
     *
     * @param username
     * @return
     */
    private OperateGrantValue orgGrantValueQuery(String username,OperateGrantConfig config){
        try {
            OperateGrantValue operateGrantValue = new OperateGrantValue();
            Set<String> keys = new HashSet<>();
            Map<String,Object> kvMap = new HashMap<>();
            List<Organization> organizations = organizationManager.listAllAuthOrganization(username, EmployeeOrgType.findByInt(config.getOrgType()).orElse(null));
            List<IndicatorOperateTree> operateOrgTree = organizationManager.getOperateOrgTree(username,config.getOrgType());
            organizations.forEach(org -> {
                keys.add(org.getOrgCode());
                kvMap.put(org.getOrgCode(),org.getOrgName());
            });
            operateGrantValue.setKeys(keys);
            operateGrantValue.setOperateGrantConfigId(config.getId());
            operateGrantValue.setName(config.getName());
            operateGrantValue.setKvMap(kvMap);
            operateGrantValue.setOrgTree(operateOrgTree);
            return operateGrantValue;
        } catch (RuntimeException e) {
            log.error("运营架构上下文查找orgGrantValueQuery异常:",e);
            throw IndicatorParamNotValidException.error(e);
        }
    }



    /**
     * 维度值精确查找
     * @param username
     * @param config
     * @return
     */
    private OperateGrantValue exactGrantValueQuery(String username,OperateGrantConfig config){
        try {
            OperateGrantValue operateGrantValue = new OperateGrantValue();
            operateGrantValue.setName(config.getName());
            operateGrantValue.setOperateGrantConfigId(config.getId());
            JdbcTemplate jdbcTemplate = getJdbcTemplate(config.getDataSource());
            String sql = generateSql(config);
            List<Map<String, Object>> queryForList = jdbcTemplate.queryForList(sql, new String[]{username});
            Map<String, Object> maps = new HashMap<>();
            if (! CollectionUtils.isEmpty(queryForList)){
                queryForList.forEach(map -> {
                    String key = Optional.ofNullable(map.get(config.getGrantColumnKey()))
                            .map(o -> o.toString())
                            .orElse(null);

                    String value = Optional.ofNullable(map.get(config.getGrantColumnValue()))
                            .map(o -> o.toString())
                            .orElse(null);
                    maps.put(key,value);
                });
                operateGrantValue.setKvMap(maps);
                operateGrantValue.setKeys(maps.keySet());

            }
            return operateGrantValue;
        } catch (RuntimeException e) {
            log.error("维度值精确查找exactGrantValueQuery异常:",e);
            throw IndicatorParamNotValidException.error(e);
        }
    }

}
