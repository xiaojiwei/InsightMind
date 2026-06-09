package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.lax.filter.EvalVisitor;
import com.graphinsight.indicator.lax.filter.LaxExprLexer;
import com.graphinsight.indicator.lax.filter.LaxExprParser;
import com.graphinsight.indicator.lax.filter.Node;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.DataQueryService;
import com.graphinsight.indicator.util.CloneUtils;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service("tableDataQuery")
public class TableDataQueryServiceImpl extends DataQueryService {

    @Autowired
    private ChartQueryService chartQueryService;

    @Override
    public PageData queryData(BuildSqlTuple tuple, PageData pageData) {

        tuple.setTable(true);

        //页面选择的维度
        Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
        //页面选择的指标
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();

        boolean isMeasureDetail = tuple.isMeasureDetail();
        QueryResult result = null;
        List<List<Cell>> cellTableList = null;
        //指标明细
        if (isMeasureDetail) {
            result = super.baseMeasureDetailQuery(tuple, pageData);
            cellTableList = super.buildCell(result);
        } else {
            result = super.baseTableQuery(tuple, pageData);

            boolean direcQuery = tuple.isDirectQuery();
            if (direcQuery) {

                PageInfo pageInfo = super.buildPageInfo(result.getValues());
                pageData.setPageInfo(pageInfo);

            } else {
                cellTableList = super.buildCell(result.getValues(), choiceDimensionSet, choiceMeasureSet);
                DataSource dataSource = tuple.getQueryParam().getDataSource();
                if (this.isDsl(dataSource)) {
                    //dsl
                    cellTableList = this.buildDSL(tuple, cellTableList, pageData);
                }
            }

        }

        QueryParam queryParam = tuple.getQueryParam();
        for (List<Cell> cells : cellTableList) {
            List<Cell> delList = new ArrayList<>();
            for (Cell cell : cells) {

                List<MeasureConfigure> measureConfigureList = queryParam.getMeasureConfigureList();
                for (MeasureConfigure measConfig : measureConfigureList) {

                    if (measConfig.getCode().equalsIgnoreCase(cell.getCode()) && measConfig.getIsHide()) {
                        delList.add(cell);
                    }
                }

            }
            cells.removeAll(delList);
        }

        pageData.setCellList(cellTableList);

        return pageData;

    }

    private boolean isDsl(DataSource dataSource) {

        List<BaseConfigure> configureList = dataSource.getConfigureList();

        for (BaseConfigure baseConfigure : configureList) {

            String exp = baseConfigure.getExpression();
            if (StringUtil.isNotEmpty(exp) && exp.length() > 1) {
                return true;
            }

        }
        return false;

    }

    public static String buildMemDate(ViewType viewType, String value, Ratio ratio) {

        RatioType ratioType = ratio.getRatioType();
        LocalDate currentDate = ChartQueryServiceImpl.formatDateStyle(viewType, true, value);
        //如果是同比，并且类型不为年,日期先统一减少一年
        if (RatioType.YEARYEMOM.equals(ratioType) && !ViewType.YEAR.equals(viewType)) {
            currentDate = currentDate.minusYears(1);
            return currentDate.format(dtf);
        }

        String dateStr = String.valueOf(value);

        if (ViewType.DAY.equals(viewType)) {

            if (RatioType.MONTHONMONTH.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusDays(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.WEEKMOM.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusWeeks(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.MONTHMOM.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusMonths(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusYears(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String ratioValue = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    LocalDate beginDate = currentDate.minusDays(Integer.valueOf(ratioValue));
                    dateStr = beginDate.format(dtf);
                } else {
                    LocalDate beginDate = currentDate.plusDays(Integer.valueOf(ratioValue));
                    dateStr = beginDate.format(dtf);
                }
            } else if (RatioType.FIEXED.equals(ratioType)) {
                String ratioValue = ratio.getRatioValue();
                currentDate = ChartQueryServiceImpl.formatDateStyle(viewType, true, ratioValue);
                dateStr = currentDate.format(dtf);
            }

        } else if (ViewType.WEEK.equals(viewType)) {

            if (RatioType.MONTHONMONTH.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusWeeks(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusYears(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String ratioValue = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    LocalDate beginDate = currentDate.minusWeeks(Integer.valueOf(ratioValue));
                    dateStr = beginDate.format(dtf);
                } else {
                    LocalDate beginDate = currentDate.plusWeeks(Integer.valueOf(ratioValue));
                    dateStr = beginDate.format(dtf);
                }
            } else if (RatioType.FIEXED.equals(ratioType)) {
                String ratioValue = ratio.getRatioValue();
                currentDate = ChartQueryServiceImpl.formatDateStyle(viewType, true, ratioValue);
                dateStr = currentDate.format(dtf);
            }

        } else if (ViewType.MONTH.equals(viewType)) {

            //近1月
            if (RatioType.MONTHONMONTH.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusMonths(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusYears(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String ratioValue = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    LocalDate beginDate = currentDate.minusMonths(Integer.valueOf(ratioValue));
                    dateStr = beginDate.format(dtf);
                } else {
                    LocalDate beginDate = currentDate.plusMonths(Integer.valueOf(ratioValue));
                    dateStr = beginDate.format(dtf);
                }
            } else if (RatioType.FIEXED.equals(ratioType)) {
                String ratioValue = ratio.getRatioValue();
                currentDate = ChartQueryServiceImpl.formatDateStyle(viewType, true, ratioValue);
                dateStr = currentDate.format(dtf);
            }

        } else if (ViewType.SEASON.equals(viewType)) {

            if (RatioType.MONTHONMONTH.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusMonths(3);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                LocalDate beginDate = currentDate.minusYears(1);
                dateStr = beginDate.format(dtf);
            } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String ratioValue = ratio.getRatioValue();
                Integer step = Integer.valueOf(ratioValue) * 3;
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    LocalDate beginDate = currentDate.minusMonths(step);
                    dateStr = beginDate.format(dtf);
                } else {
                    LocalDate beginDate = currentDate.plusMonths(step);
                    dateStr = beginDate.format(dtf);
                }
            } else if (RatioType.FIEXED.equals(ratioType)) {
                String ratioValue = ratio.getRatioValue();
                currentDate = ChartQueryServiceImpl.formatDateStyle(viewType, true, ratioValue);
                dateStr = currentDate.format(dtf);
            }

        } else if (ViewType.YEAR.equals(viewType)) {

            //近一年
            if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String ratioValue = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    LocalDate beginDate = currentDate.minusYears(Integer.valueOf(ratioValue));
                    dateStr = beginDate.format(dtf);
                } else {
                    LocalDate beginDate = currentDate.plusYears(Integer.valueOf(ratioValue));
                    dateStr = beginDate.format(dtf);
                }
            }  else if (RatioType.FIEXED.equals(ratioType)) {
                String ratioValue = ratio.getRatioValue();
                currentDate = ChartQueryServiceImpl.formatDateStyle(viewType, true, ratioValue);
                dateStr = currentDate.format(dtf);
            } else {
                LocalDate beginDate = currentDate.minusYears(1);
                dateStr = beginDate.format(dtf);
            }

        }

        return dateStr;

    }

    public static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static Filter buildMemFilter(Dimension radioDim, Ratio ratio, List<Cell> cellList) {

        Filter filter = new Filter();
        //1、找到对应维度的值
        String dimCode = radioDim.getCode();
        filter.setCode(dimCode);
        List<Operator> operatorList = new LinkedList<>();

        for (Cell cell : cellList) {

            //找到同环比维度
            if (cell.getCode().equalsIgnoreCase(dimCode)) {
                //维度值
                String value = cell.getData();
                //维度类型 日、周、月、季、年。
                ViewType viewType = cell.getViewType();
                //2、根据维度类型同环比ratio类型，计算新的筛选值
                String memDate = buildMemDate(viewType, value, ratio);
                LocalDate begin = ChartQueryServiceImpl.formatDateStyle(viewType, true, memDate);
                LocalDate end = ChartQueryServiceImpl.formatDateStyle(viewType, false, memDate);

                Operator operator = new Operator();
                operator.setTimeRange(TimeRange.DATE);
                operator.setSqlOprType(SqlOprType.BETEEN);
                operator.getDataList().add(begin.toString());
                operator.getDataList().add(end.toString());

                operatorList.add(operator);

            }

        }

        filter.setOperatorList(operatorList);
        //3、构建MemFilter
        return filter;

    }

    public static List<Filter> buildCellFilters(Dimension radioDim, List<Cell> cellList) {

        List<Filter> filterList = new LinkedList<>();

        for (Cell cell : cellList) {

            if (CellType.DIMENSION.equals(cell.getType())) {

                //同环比维度自动忽略
                if (cell.getCode().equalsIgnoreCase(radioDim.getCode())) {
                    continue;
                }

                String code = cell.getCode();
                String data = cell.getData();
                String id = cell.getId();

                Filter filter = new Filter();
                filter.setCode(code);

                Operator operator = new Operator();
                operator.getDataList().add(id);
                operator.setSqlLogicalType(SqlLogicalType.AND);
                operator.setSqlOprType(SqlOprType.IN);

                if (IndicatorConstant.BI_NULL.equalsIgnoreCase(id)) {
                    operator.getDataList().add("null");
                    operator.setSqlOprType(SqlOprType.IN);
                }

                if (BuildSqlServiceImpl.isDateViewType(cell.getViewType())) {
                    operator.setTimeRange(TimeRange.DATE);
                }

                filter.getOperatorList().add(operator);
                filterList.add(filter);

            }

        }

        return filterList;

    }

    /**
     * 删除同环比日期维度的筛选条件，添加新的同环比筛选条件。
     * @param filterList
     * @param radioDim
     * @return
     */
    public static List<Filter> buildMemFilters(List<Filter> filterList, Dimension radioDim, Ratio ratio, List<Cell> cellList) {

        List<Filter> copyFilterList = new LinkedList<>();
        for (Filter filter : filterList) {
            String filterCode = filter.getCode();

            //如果是同环比的维度筛选项,则增加同环比筛选条件，否则直接添加。。
            if (radioDim.getCode().equals(filterCode)) {
                continue;
            } else {
                Filter copy = CloneUtils.clone(filter);
                copyFilterList.add(copy);
            }
        }

        //追加新同环比的筛选项
        Filter memFilter = buildMemFilter(radioDim, ratio, cellList);
        copyFilterList.add(memFilter);

        return copyFilterList;
    }


    //计算该行数据同环比
    public static String memExec(List<Cell> cellList, Ratio ratio, BaseConfigure baseConfigure, BuildSqlTuple buildSqlTuple, ChartQueryService chartQueryService) {

        //1 目标指标
        String measCode = baseConfigure.getCode();
        //2 相关维度
        Set<Dimension> dimSet = buildSqlTuple.getDimensionSet();
        //3 相关筛选条件
        List<Filter> filterList = buildSqlTuple.getQueryParam().getFilterList();
        //4 去掉同环比对应的日期筛选条件，并增加同环比筛选条件。
        Dimension radioDim = buildSqlTuple.getOnlyRadioDim();

        if (null == radioDim) {
            radioDim = BuildSqlServiceImpl.getDimByRadio(buildSqlTuple);
        }
        //根据筛选条件的filterList,同时构建同环比日期筛选项
        List<Filter> memFilterList = buildMemFilters(filterList, radioDim, ratio, cellList);
        List<Filter> cellFilterList = buildCellFilters(radioDim, cellList);

        //所有过滤筛选项完成
        List<Filter> allFilterList = new ArrayList<>();
        allFilterList.addAll(memFilterList);
        allFilterList.addAll(cellFilterList);

        //6 构建DataSource
        DataSource dataSource = new DataSource();
        //spaceId
        dataSource.setSpaceId(buildSqlTuple.getQueryParam().getDataSource().getSpaceId());
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        //指标构建
//        BaseConfigure measConfig = new BaseConfigure();
//        measConfig.setCode(measCode);
//        dataSource.getConfigureList().add(measConfig);
        List<BaseConfigure> configureList = buildSqlTuple.getQueryParam().getDataSource().getConfigureList();
        if (!CollectionUtils.isEmpty(configureList)) {
            for (BaseConfigure configure : configureList) {
                String code = configure.getCode();

                if (code.indexOf("MEAS_") >= 0) {
                    BaseConfigure measConfig = new BaseConfigure();
                    measConfig.setCode(configure.getCode());
                    measConfig.setExpression(configure.getExpression());
                    dataSource.getConfigureList().add(measConfig);
                }

            }
        }

        //维度构建
        if (!CollectionUtils.isEmpty(dimSet)) {
            for (Dimension dim : dimSet) {
                BaseConfigure dimConfig = new BaseConfigure();
                dimConfig.setCode(dim.getCode());
                dataSource.getConfigureList().add(dimConfig);
            }
        }
        //删选过滤条件
        dataSource.getFilterList().addAll(allFilterList);

        String resultData = null;
        //7 执行
        PageData pageDate = chartQueryService.execQuery(dataSource);
        List<List<Cell>> rowsList = pageDate.getCellList();
        if (!CollectionUtils.isEmpty(rowsList) && rowsList.size() > 0) {
            List<Cell> rowList = rowsList.get(0);
            resultData = findMeasure(rowList, measCode);

        }

        return resultData;

    }

    private static String findMeasure(List<Cell> cellList, String code) {

        for (Cell cell : cellList) {

            if (code.equalsIgnoreCase(cell.getCode())) {
                return cell.getData();
            }

        }

        return null;

    }



    private List<List<Cell>> buildDSL(BuildSqlTuple tuple, List<List<Cell>> cellTableList, PageData pageData) {

        //配置信息
        List<BaseConfigure> configureList = tuple.getQueryParam().getDataSource().getConfigureList();

//        int numThreads = Runtime.getRuntime().availableProcessors();
//        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        configureList = Collections.synchronizedList(configureList);

        configureList.parallelStream().forEach(baseConfigure -> {

            UserThreadLocalUtil.setUserName(tuple.getQueryParam().getUsername());

//        for (BaseConfigure baseConfigure : configureList) {

            String exp = baseConfigure.getExpression();
            if (StringUtil.isNotEmpty(exp)) {

                CharStream cs = CharStreams.fromString(exp);
                LaxExprLexer lexer = new LaxExprLexer(cs);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                LaxExprParser parser = new LaxExprParser(tokens);
                ParseTree tree = parser.prog(); // parse

                for (List<Cell> cells : cellTableList) {

                    EvalVisitor eval = new EvalVisitor(tuple, pageData, cellTableList, cells, this.chartQueryService);
                    Node obj = eval.visit(tree);
                    Cell ldxCell = new Cell();
                    String orgValue = String.valueOf(obj.result);
                    orgValue = this.format(orgValue, baseConfigure.getValueFormat());
                    ldxCell.setData(orgValue);
                    ldxCell.setName(baseConfigure.getName());
                    ldxCell.setCode(baseConfigure.getCode());
                    ldxCell.setType(CellType.MEASURE);

                    cells.add(ldxCell);

                    List<Ratio> ratioList = baseConfigure.getRatioList();
                    if (!CollectionUtils.isEmpty(ratioList)) {
                        for (Ratio ratio : ratioList) {

                            String memValue = this.memExec(cells, ratio, baseConfigure, tuple, this.chartQueryService);
                            try {
                                if (null != memValue && null != orgValue && !"-".equalsIgnoreCase(orgValue) && !"-".equalsIgnoreCase(memValue)) {

                                    memValue = memValue.replaceAll(",", "");
                                    orgValue = orgValue.replaceAll(",", "");

                                    Double memValueD = Double.valueOf(memValue);
                                    Double orgValueD = Double.valueOf(orgValue);

                                    Cell.Ratio memRatio = new Cell.Ratio();

                                    memRatio.setRatioType(ratio.getRatioType().toString());
                                    memRatio.setValue(String.valueOf(memValueD));
                                    Double ratioValue = this.execRatioExp(ratio.getRatioExpType(), orgValueD, memValueD);
                                    ratioValue = ratioValue * 100;
                                    String ratioValueStr = String.format("%.2f", ratioValue) + "%";
                                    memRatio.setRatio(ratioValueStr);

                                    ldxCell.getRatioList().add(memRatio);

                                } else {

                                    Cell.Ratio memRatio = new Cell.Ratio();

                                    memRatio.setRatioType(ratio.getRatioType().toString());
                                    memRatio.setValue("-");
//                                    Double ratioValue = this.execRatioExp(ratio.getRatioExpType(), orgValueD, memValueD);
//                                    ratioValue = ratioValue * 100;
//                                    String ratioValueStr = String.format("%.2f", ratioValue) + "%";
                                    memRatio.setRatio("-");

                                    ldxCell.getRatioList().add(memRatio);

                                }

                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }

                }
            }
        });

        int len = configureList.size() - 1;

        for (int i = len; i >= 0; i--) {
            BaseConfigure baseConfigure = configureList.get(i);
            Order order = baseConfigure.getOrder();
            if (null == order) {
                continue;
            }

            SortType sortType = order.getSortType();

            if (null == sortType || SortType.DEFAULT.equals(sortType)) {
                continue;
            }

            String code = baseConfigure.getCode();

            cellTableList.sort(new Comparator<List<Cell>>() {
                @Override
                public int compare(List<Cell> o1, List<Cell> o2) {

                    Cell cell1 = findCell(code, o1);
                    Cell cell2 = findCell(code, o2);

                    int compare = 0;

                    if (CellType.MEASURE.equals(cell1.getType())) {

                        try {
                            Double value1 = format(cell1.getData());
                            Double value2 = format(cell2.getData());

                            if (SortType.ASC.equals(sortType)) {
                                compare = value1.compareTo(value2);
                            } else {
                                compare = value2.compareTo(value1);
                            }
                        } catch (Exception ex) {

                            //ascii 码对比
                            ex.printStackTrace();

                            if (SortType.ASC.equals(sortType)) {
                                compare = cell1.getData().compareTo(cell2.getData());
                            } else {
                                compare = cell2.getData().compareTo(cell1.getData());
                            }

                        }

                    } else if (CellType.DIMENSION.equals(cell1.getType())) {

                        if (SortType.ASC.equals(sortType)) {
                            compare = cell1.getData().compareTo(cell2.getData());
                        } else {
                            compare = cell2.getData().compareTo(cell1.getData());
                        }

                    }

                    return compare;
                }
            });

        }

        return cellTableList;

    }

    class CellProcessor implements Runnable {
        private final List<Cell> cells;
        private BuildSqlTuple tuple;
        private PageData pageData;
        private List<List<Cell>> cellList;
        private ChartQueryService chartQueryService;

        private ParseTree tree;

        private BaseConfigure baseConfigure;

        public CellProcessor(BuildSqlTuple buildSqlTuple, PageData pageData, List<List<Cell>> cellList, List<Cell> rowCells, ChartQueryService chartQueryService
                                    ,ParseTree tree, BaseConfigure baseConfigure) {
            this.cells = rowCells;
            this.tuple = buildSqlTuple;
            this.pageData = pageData;
            this.cellList = cellList;
            this.chartQueryService = chartQueryService;
            this.tree = tree;
            this.baseConfigure = baseConfigure;
        }

        @Override
        public void run() {

            EvalVisitor eval = new EvalVisitor(this.tuple, pageData, cellList, cells, this.chartQueryService);
            Node obj = eval.visit(tree);
            Cell ldxCell = new Cell();
            String orgValue = String.valueOf(obj.result);
            orgValue = TableDataQueryServiceImpl.format(orgValue, baseConfigure.getValueFormat());
            ldxCell.setData(orgValue);
            ldxCell.setName(baseConfigure.getName());
            ldxCell.setCode(baseConfigure.getCode());
            ldxCell.setType(CellType.MEASURE);

            cells.add(ldxCell);

            List<Ratio> ratioList = baseConfigure.getRatioList();
            if (!CollectionUtils.isEmpty(ratioList)) {
                for (Ratio ratio : ratioList) {

                    String memValue = memExec(cells, ratio, baseConfigure, tuple, this.chartQueryService);
                    try {
                        if (null != memValue && null != orgValue && !"-".equalsIgnoreCase(orgValue) && !"-".equalsIgnoreCase(memValue)) {

                            memValue = memValue.replaceAll(",", "");
                            orgValue = orgValue.replaceAll(",", "");

                            Double memValueD = Double.valueOf(memValue);
                            Double orgValueD = Double.valueOf(orgValue);

                            Cell.Ratio memRatio = new Cell.Ratio();

                            memRatio.setRatioType(ratio.getRatioType().toString());
                            memRatio.setValue(String.valueOf(memValueD));
                            Double ratioValue = execRatioExp(ratio.getRatioExpType(), orgValueD, memValueD);
                            ratioValue = ratioValue * 100;
                            String ratioValueStr = String.format("%.2f", ratioValue) + "%";
                            memRatio.setRatio(ratioValueStr);

                            ldxCell.getRatioList().add(memRatio);

                        } else {

                            Cell.Ratio memRatio = new Cell.Ratio();

                            memRatio.setRatioType(ratio.getRatioType().toString());
                            memRatio.setValue("-");
//                                    Double ratioValue = this.execRatioExp(ratio.getRatioExpType(), orgValueD, memValueD);
//                                    ratioValue = ratioValue * 100;
//                                    String ratioValueStr = String.format("%.2f", ratioValue) + "%";
                            memRatio.setRatio("-");

                            ldxCell.getRatioList().add(memRatio);

                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    public static String format(String srcValue, ValueFormat valueFormat) {

        if (null != srcValue && !"-".equalsIgnoreCase(srcValue)) {
            srcValue = srcValue.replaceAll(",", "");
        } else {
            return null;
        }

        String formatColumn = null;
        if (null == valueFormat) {
            //容错，如果未设置，默认取null
            valueFormat = new ValueFormat();
            valueFormat.setFormatType(FormatType.DECIMAL);
            valueFormat.setValue(6);
//            valueFormat.setFormatType(FormatType.THOUSANDTH);
        }

        FormatType formatType = valueFormat.getFormatType();

        BigDecimal originalValue = null;
        try {
            originalValue = new BigDecimal(srcValue);
        } catch (Exception ex) {
            System.err.println(srcValue);
            ex.printStackTrace();
            return srcValue;
        }

        BigDecimal roundedValue = originalValue;
        int scale = valueFormat.getValue();

        if (FormatType.DECIMAL.equals(formatType)) {
            //自定义小数
            Integer value = valueFormat.getValue();
            roundedValue = originalValue.setScale(value, RoundingMode.HALF_UP);
            formatColumn = roundedValue.toString();

        } else if (FormatType.DECIMAL1.equals(formatType)) {
//            formatColumn = "round(" + column + ", 1)";
            roundedValue = originalValue.setScale(1, RoundingMode.HALF_UP);
            formatColumn = roundedValue.toString();
        } else if (FormatType.DECIMAL2.equals(formatType)) {
//            formatColumn = "round(" + column + ", 2)";
            roundedValue = originalValue.setScale(2, RoundingMode.HALF_UP);
            formatColumn = roundedValue.toString();
        } else if (FormatType.INTEGER.equals(formatType)) {
//            formatColumn = "round(" + column + ", 0)";
            roundedValue = originalValue.setScale(0, RoundingMode.HALF_UP);
            formatColumn = roundedValue.toString();
        } else if (FormatType.PERCENT.equals(formatType)) {
            //自定义小数
            Integer value = valueFormat.getValue();
//            formatColumn = "concat(round(" + column + "*100, " + value + "), '%')";
            originalValue = new BigDecimal(originalValue.doubleValue() * 100);
            roundedValue = originalValue.setScale(value, RoundingMode.HALF_UP);
            formatColumn = roundedValue.toString() + "";

        } else if (FormatType.PERCENT1.equals(formatType)) {
//            formatColumn = "concat(round(" + column + "*100, 1), '%')";
            originalValue = new BigDecimal(originalValue.doubleValue() * 100);
            roundedValue = originalValue.setScale(1, RoundingMode.HALF_UP);
            formatColumn = roundedValue.toString() + "";

        } else if (FormatType.PERCENT2.equals(formatType)) {
//            formatColumn = "concat(round(" + column + "*100, 2), '%')";
            originalValue = new BigDecimal(originalValue.doubleValue() * 100);
            roundedValue = originalValue.setScale(2, RoundingMode.HALF_UP);
            formatColumn = roundedValue.toString() + "";
        } else if (FormatType.THOUSANDTH.equals(formatType)) {
            formatColumn = roundedValue.toString() + "";
//            DecimalFormat df = new DecimalFormat("#,##0.00");
//            formatColumn = df.format(originalValue.doubleValue());
//            formatColumn = "case when " + column + " >= 1 or " + column + " <= -1 then case starts_with(regexp_replace(money_format(" + column + "), '\\\\.00', ''), '.') when 1 then concat('0', regexp_replace(money_format(" + column + "), '\\\\.00', '')) else regexp_replace(money_format(" + column + "), '\\\\.00', '') end when " + column + " = 0 then 0 else cast(round(" + column + ", 4) as string) end";
        } else if (FormatType.MILLION.equals(formatType)) {
            formatColumn = roundedValue.toString();
        } else {
            formatColumn = roundedValue.toString();
        }

        return formatColumn;

    }


    private static Double format(String value) {
        value = value.replaceAll(",", "");
        return Double.valueOf(value);
    }

    private static Cell findCell(String code, List<Cell> cellList) {
        if (!CollectionUtils.isEmpty(cellList)) {
            for (Cell cell : cellList) {
                if (cell.getCode().equalsIgnoreCase(code)) {
                    return cell;
                }
            }
        }
        return null;
    }

    public static Double execRatioExp(RatioExpType ratioExpType, Double org, Double mom) {

        Double ratioExp = null;
        if (RatioExpType.DIFFPERCENTAGE.equals(ratioExpType) || null == ratioExpType) {
            ratioExp = ((org - mom)/ mom);
        } else if (RatioExpType.DIFF.equals(ratioExpType)) {
            ratioExp = (org - mom);
        } else if (RatioExpType.PERCENTAGE.equals(ratioExpType)) {
            ratioExp = (org / mom);
        }

        return ratioExp;

    }

    public static void main(String[] args) {
        System.out.println("test");
    }

}
