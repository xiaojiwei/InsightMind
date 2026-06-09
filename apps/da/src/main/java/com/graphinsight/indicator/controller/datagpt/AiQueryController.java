package com.graphinsight.indicator.controller.datagpt;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.TSpaceMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.controller.BaseController;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.BaseConfigure;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.KeyWord2Service;
import com.graphinsight.indicator.service.gpt4.LiCloudGptClient;
import com.graphinsight.indicator.service.impl.TextToSqlService;
import com.graphinsight.indicator.service.wordNlp.WordSyntax;
import com.graphinsight.indicator.service.wordNlpV2.parse.TreeParse;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.TempThreadLocalUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@DS("mysql")
@RestController
@RequestMapping(IndicatorConstant.DATA_GPT_AI)
@Api(tags = "【dataGpt】查询模块接口 ")
public class AiQueryController extends BaseController {

    @Autowired
    UserMapper userMapper;
    @Autowired
    WordSyntax wordSyntax;
    @Autowired
    KeyWord2Service keyWord2Service;
    @Autowired
    private ChartQueryService chartQueryService;

    @PostMapping("/org/query")
    public Response queryData(@RequestBody DataQueryVO dataQueryVO) {
        String error = "";
        if (dataQueryVO.getUsername() != null) {
            User user = userMapper.selectByUsername(dataQueryVO.getUsername());
            UserThreadLocalUtil.set(user);
        }
        // todo 临时替换下顺序
        try {
            String word = dataQueryVO.getWord();
            PageData pageData = keyWord2Service.doAction2(dataQueryVO, word, dataQueryVO.getIsData());
            if (!pageData.getDataRange()) {
                return Response.ok(pageData);
            } else {
                if (pageData.getCellList().isEmpty()) {
                    return Response.ok(pageData);
                } else {
                    return Response.ok(pageData);
                }
            }

        } catch (Exception e) {

            error += "nlp方式查询报错：" + e.getMessage() + "\n";
            log.info("nlp方式查询报错：{}", e.getMessage());
            //return Response.error("后端查询失败，errorMsg: \n" + error);
        }

        //return Response.error("后端查询失败，errorMsg: \n" + error);
        return Response.ok();
    }

    @Autowired
    TSpaceMapper tSpaceMapper;

    @Autowired
    ITSpaceService itSpaceService;

    @Autowired
    CacheManager cacheManager;

    @PostMapping(value = "/datasource/query")
    @ResponseBody
    @IgnoreWebLog
    public Response query(@RequestBody DataSource dataSource) {

        Long begin = System.currentTimeMillis();
        UserThreadLocalUtil.setBeginTime();

        //获取用户名
        String userName = dataSource.getUsername();
        if (StringUtil.isEmpty(userName)) {
            userName = UserThreadLocalUtil.getUserName();
            dataSource.setUsername(userName);
        }

        Map<String, BaseConfigure> addConfigureMap = new HashMap<>();
        Map<String, BaseConfigure> delConfigureMap = new HashMap<>();
        if (!dataSource.getAddDim().isEmpty()) {
            for (BaseConfigure configure : dataSource.getAddDim()) {
                dataSource.setWordText(dataSource.getWordText() + "、" + configure.getName());
                addConfigureMap.put(configure.getCode(), configure);
            }
        }
        if (!dataSource.getDeleteDim().isEmpty()) {
            for (BaseConfigure configure : dataSource.getDeleteDim()) {
                String delText = dataSource.getWordText().replace(configure.getName(), "");
                dataSource.setWordText(delText);
                delConfigureMap.put(configure.getCode(), configure);
            }
        }

        if (!dataSource.getDeleteMesa().isEmpty()) {
            for (BaseConfigure configure : dataSource.getDeleteMesa()) {
                String delText = dataSource.getWordText().replace(configure.getName(), "");
                dataSource.setWordText(delText);
                delConfigureMap.put(configure.getCode(), configure);
            }
        }

        for (int i = dataSource.getConfigureList().size() - 1; i >= 0; i--) {
            BaseConfigure configure = dataSource.getConfigureList().get(i);
            if (null != delConfigureMap.get(configure.getCode())) {
                dataSource.getConfigureList().remove(i);
                continue;
            }
            if (configure.getCode().contains("DIM") && !configure.getCode().contains("DIM_FAKE_")) {
                dataSource.getDimConfList().add(configure);
            }
            if (configure.getCode().contains("MEAS") && null != configure.getId()) {
                MeasureCache measure = cacheManager.getMeasureCache(Math.toIntExact(configure.getId()));
                if (!measure.getMeasureApplicationCacheList().isEmpty()) {
                    AiFrontFormatVo aiFrontFormatVo = new AiFrontFormatVo();
                    DataFormatType dataFormatType = DataFormatType.findNullableByString(measure.getMeasureApplicationCacheList().get(0).getDataFormatStr());
                    aiFrontFormatVo.setType(dataFormatType.getCode());
                    aiFrontFormatVo.setDecimalPlaces(measure.getMeasureApplicationCacheList().get(0).getDecimalPlaces());
                    aiFrontFormatVo.setDataScale(DecimalFormatType.findNullableByCode(measure.getMeasureApplicationCacheList().get(0).getDataScale()));
                    configure.setFormat(aiFrontFormatVo);
                }
            }


        }
        PageData pageData = null;
        Response<PageData> response = null;
        if (!Objects.equals(dataSource.getRouteType(), "nlp")) {
            pageData = keyWord2Service.queryDetail(dataSource);
            pageData.setDataSource(dataSource);
            response = Response.ok("查询成功", pageData);
            return response;
        }


        // List<TSpace> tSpaceList = tSpaceMapper.selectList(Wrappers.<TSpace>lambdaQuery().orderByDesc(TSpace::getId));
        TSpace tSpace = itSpaceService.getAiSpaceById();
        dataSource.setSpaceId(tSpace.getId());


        Long spaceId = dataSource.getSpaceId();
        if (null == spaceId) {
            spaceId = 4L;
        }
        if (null == spaceId && !isSuperAdmin(userName)) {

            log.error("调用异常:", "spaceId is null");
            response = Response.error("查询失败,spaceId为null，请联系开发.");

            response.setErrorType(ResponseErrorType.SYSTEM);
            response.setErrorOwner("doulinxu1");//系统级错误先指定开发

            response.setErrorMessage("spaceId is null");

        } else {

            try {

                DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
                //正常查询都走直查
                dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
                dataSource.setPageSize(99999999);
                pageData = this.chartQueryService.execQuery(dataSource);
                pageData.setBaseInfoMap(new HashMap<>());
                keyWord2Service.buildBaseMap(dataSource, pageData);
                pageData.setCost(System.currentTimeMillis() - begin);
                response = Response.ok("查询成功", pageData);

//                this.chartQueryService.addQueryLog(dataSource, pageData);
                pageData.setLoginUserName(userName);
                pageData.setDataSource(dataSource);
                UserThreadLocalUtil.printCost("DataSourceController.end");

            } catch (Exception ex) {
                ex.printStackTrace();
                log.error("调用异常:", ex);
                response = Response.error("查询失败");

                if (ex instanceof IllegalArgumentException) {
                    String owenr = String.valueOf(TempThreadLocalUtil.get("owner"));
                    response.setErrorOwner(owenr);
                    response.setErrorType(ResponseErrorType.DATA);
                } else {
                    response.setErrorType(ResponseErrorType.SYSTEM);
                    response.setErrorOwner("xiaojiwei");//系统级错误先指定开发
                }

                response.setErrorStackTrace(ex.getStackTrace());
                response.setErrorMessage(ex.toString());
            }

        }

        return response;

    }

    @Autowired
    ITSuperAdminService itSuperAdminService;

    private boolean isSuperAdmin(String username) {
        boolean res = false;
        try {
            List<TSuperAdmin> list = itSuperAdminService.list();
            Set<String> names = list.stream().map(TSuperAdmin::getEmpCode).collect(Collectors.toSet());
            if (names.contains(username)) {
                res = true;
            }
        } catch (Exception e) {
            log.error("超级管理员查询失败", e);
        }
        return res;
    }

    @Autowired
    private UserManager userManager;
    @Autowired
    private LiCloudGptClient liCloudGptClient;

    @PostMapping("/query")
    public Response querySplitData(@RequestBody DataQueryVO dataQueryVO) {
        String error = "";
        PageData pageData = new PageData();
        UserThreadLocalUtil.setBeginTime();

        //获取用户名
        String userName = dataQueryVO.getUsername();
        if (StringUtil.isEmpty(userName)) {
            userName = UserThreadLocalUtil.getUserName();
            dataQueryVO.setUsername(userName);
        }
        // 分词校验
        String wordTextInfo = dataQueryVO.getWord();
        wordTextInfo = wordTextInfo.replaceAll("[\\p{Punct}\\s]", "").replaceAll("[。|，|！|？|、|；|：|“|”|‘|’|《|》|（|）|【|】|—|……|·|~|——]", "");
        if (wordTextInfo.isEmpty()) {
            keyWord2Service.recordQuestInfo(dataQueryVO.getWord(), null, pageData);
            String gptInfo = liCloudGptClient.textToInfo(dataQueryVO.getWord());
            pageData.setChatText(gptInfo);
            return Response.ok("请输入有效的查询条件!", pageData);
        }

        String wordText = dataQueryVO.getWord();
        wordText = wordText.replaceAll("[!.?;:]", "");
        // 获取分词元素值
        String word = wordText.replaceAll("\\s+", "");
        word = word.replaceAll("(\\d),(?=\\d{3})", "$1");


        dataQueryVO.setWord(word.toLowerCase());

        WordSyntaxVo wordSyntaxVo = wordSyntax.splitInfo(dataQueryVO.getWord());
        UserThreadLocalUtil.printCost("splitList chain");


        if (wordSyntaxVo.getIsBoard()) {
            String boardUrl = "";
            for (WordSyntaxVo.WordItem wordItemBoard : wordSyntaxVo.getWordItemList()) {
                if (wordItemBoard.getMatchType() != null && wordItemBoard.getMatchType().equals("board")) {
                    if (!wordItemBoard.getValueList().isEmpty()) {
                        List<String> valList = Arrays.asList(wordItemBoard.getValueList().get(0).split("~"));
                        if (valList.size() > 1) {
                            boardUrl = valList.get(1);
                        }
                        break;
                    }

                }
            }
            if (!Objects.equals(boardUrl, "")) {
                pageData.setBoardUrl(boardUrl);
                DataSource dataSourceBoard = new DataSource();
                dataSourceBoard.setBoard(true);
                keyWord2Service.recordQuestInfo(dataQueryVO.getWord(), dataSourceBoard, pageData);
            }

            return Response.ok(pageData);
        }
        DataSource dataSource = wordSyntax.buildDataSource(wordSyntaxVo);
        UserThreadLocalUtil.printCost("dataSource");
        if (dataSource == null) {
            keyWord2Service.recordQuestInfo(dataQueryVO.getWord(), null, pageData);
            String gptInfo = liCloudGptClient.textToInfo(dataQueryVO.getWord());
            pageData.setChatText(gptInfo);
            return Response.ok("请输入有效的查询条件!", pageData);
        }


        dataSource.setData(dataQueryVO.getIsData());
        dataSource.setWordText(dataQueryVO.getWord());
        try {
            // 没有识别到指标
            if (!dataSource.getNoDataRangeList().isEmpty() && dataSource.getMeasConfList().isEmpty()) {
                pageData.setDataRange(false);
                pageData.setDataAllRange(false);

                Set<String> createBys = new HashSet<>();
                createBys.add("lipengkai");
                List<User> userList = userManager.listUserByUsernames(createBys);
                // 获取管家信息
                String tip = String.join("、", dataSource.getNoDataRangeList());
                Map<String, Object> rangeInfo = new HashMap<>();
                rangeInfo.put("tip", tip);
                rangeInfo.put("users", userList);
                pageData.setRangeInfo(rangeInfo);
            } else {
                if (dataSource.getMeasConfList().isEmpty()
                        && (!dataSource.getConfigureList().isEmpty() || !dataSource.getFilterList().isEmpty())) {
                    pageData = keyWord2Service.queryDetail(dataSource);
                } else {
                    dataSource.setUsername(dataQueryVO.getUsername());
                    dataSource.setUseCache(dataQueryVO.getUseCache());
//                    dataSource.setWordSyntaxVo(wordSyntaxVo);
                    pageData = keyWord2Service.queryNlp(dataSource);
                }
            }


            if (!pageData.getDataRange()) {
                keyWord2Service.recordQuestInfo(dataQueryVO.getWord(), null, pageData);
                return Response.ok(pageData);
            } else {
                if (pageData.getRecordSuccess()) {
                    keyWord2Service.recordQuestInfo(dataQueryVO.getWord(), dataSource, pageData);
                } else {
                    keyWord2Service.recordQuestInfo(dataQueryVO.getWord(), null, pageData);
                }
                if (pageData.getCellList().isEmpty()) {
                    return Response.ok(pageData);
                } else {
                    return Response.ok(pageData);
                }
            }

        } catch (Exception e) {
            keyWord2Service.recordQuestInfo(dataQueryVO.getWord(), dataSource, pageData);
            error += "data gpt 方式查询报错：" + e.getMessage() + "\n";
            log.info("data gpt 方式查询报错：{}", e.getMessage(), e);
            //return Response.error("后端查询失败，errorMsg: \n" + error);
            return Response.ok("输入的问题未识别到数据，请更换一个输入问题");
            //return Response.error("后端查询失败，errorMsg: \n" + error);
        }

        // return Response.error("后端查询失败，errorMsg: \n" + error);
        //return Response.ok();
    }

    @Autowired
    TreeParse treeParse;

    @PostMapping("/tree/query")
    public Response queryTreeData(@RequestBody DataQueryVO dataQueryVO) {
        TextNodeVo tree = treeParse.textParse(dataQueryVO.getWord());
        return Response.ok(tree);
    }


    @PostMapping("/tree/queryMap")
    public Response queryTreeMapData(@RequestBody DataQueryVO dataQueryVO) {

        return Response.ok(treeParse.textParseTest(dataQueryVO.getWord()));
    }

    @Autowired
    TextToSqlService textToSqlService;

    @PostMapping("/text/toSql")
    public Response queryTextToSql(@RequestBody DataQueryVO dataQueryVO) {

        return Response.ok(textToSqlService.textToSql(dataQueryVO.getWord(), dataQueryVO.getUsername()));
    }

    @PostMapping("/query/toSql")
    public Response queryToSql(@RequestBody DataQueryVO dataQueryVO) {

        return Response.ok(textToSqlService.textToSql(dataQueryVO.getWord(), dataQueryVO.getUsername()));
    }
}
