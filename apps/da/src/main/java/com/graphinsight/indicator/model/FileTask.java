package com.graphinsight.indicator.model;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.csvreader.CsvWriter;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.ChartType;
import com.graphinsight.indicator.enums.FileDownStatus;
import com.graphinsight.indicator.enums.MemberType;
import com.graphinsight.indicator.enums.RatioType;
import com.graphinsight.indicator.service.PivotService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.service.impl.BosFileServiceImpl;
import com.graphinsight.indicator.service.impl.PivotServiceImpl;
import com.graphinsight.indicator.util.StringUtil;
import lombok.Data;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 文件下载task
 */
@Data
public class FileTask extends Thread {

    /**
     * 下载文件信息
     */
    private FileDownInfo fileDownInfo;

    /**
     * redis 工具类
     */
    private RedisCacheService redisCacheService;

    private Connection connection;

    private BuildSqlTuple tuple;

    @Override
    public void run() {

        /**
         * 核心下载文件方法
         */
        String downId = this.fileDownInfo.getDownloadId();
        this.setName("FileDown_" + this.fileDownInfo.getDownloadId());

        fileDownInfo.setFileDownStatus(FileDownStatus.RUNING);
        redisCacheService.put(downId, fileDownInfo);

        try {

            Connection conn = this.getConnection();
            BigInteger cnt = this.getCount(conn);
            fileDownInfo.setCount(cnt);
            redisCacheService.put(downId, fileDownInfo);

            this.writeFile(conn);

        } catch (Exception ex) {
            ex.printStackTrace();
            fileDownInfo.setFileDownStatus(FileDownStatus.FAIL);
            fileDownInfo.setMessage(ex.getMessage());
            redisCacheService.put(downId, fileDownInfo);
        }

    }

    private void writeFile(Connection conn) {

        BigInteger highWaterline = BigInteger.valueOf(20100);
        BigInteger cnt = this.fileDownInfo.getCount();
        this.tuple = this.fileDownInfo.getTuple();
        QueryParam queryParam = tuple.getQueryParam();
        ChartType chartType = queryParam.getChartType();

        if (ChartType.PIVOT.equals(chartType)) {
            //交叉表导出
            this.writePivotXls(conn);
        } else {
            if (highWaterline.compareTo(cnt) < 0) {
                this.writeCsv(conn);
            } else {
                this.writeXls(conn);
            }
        }

    }

    private void writePivotXls(Connection conn) {

        String downId = this.fileDownInfo.getDownloadId();
        String sql = this.fileDownInfo.getSql();

        PivotService pivotService = new PivotServiceImpl();

        try {

            BigInteger progress = BigInteger.valueOf(0);
            BigInteger zero = BigInteger.valueOf(0);
            //每写入flushNumber行后，输出到对象状态。
            BigInteger flushNumber = BigInteger.valueOf(1000);

            PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Integer.MIN_VALUE);
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<Map<String, Object>> valueMapList = new ArrayList<Map<String, Object>>();
            while (rs.next()) {

                progress = progress.add(BigInteger.ONE);

                LinkedHashMap<String, Object> rowMap = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String label = metaData.getColumnLabel(i);
                    String value = rs.getString(label);
                    rowMap.put(label, value);
                }

                if (progress.mod(flushNumber).equals(zero)) {
                    fileDownInfo.setProgress(progress);
                    redisCacheService.put(downId, fileDownInfo);
                    //异步导出大文件优先级别不高，每写flushNumber后,cpu释放200ms，防止cpu拉满。
                    TimeUnit.MILLISECONDS.sleep(200);
                }

                valueMapList.add(rowMap);
            }

            QueryResult result = new QueryResult();
            result.setValueMap(valueMapList);

            Matrix matrix = pivotService.buildMatrix(tuple, result);

            String fileName = writePivotXls(matrix);
            //发送BOS上
            String fileUpKey = BosFileServiceImpl.writeBos(fileName);
            // 记录上传ID
            fileDownInfo.setFileKey(fileUpKey);
            fileDownInfo.setFileName(fileName);
            fileDownInfo.setFileDownStatus(FileDownStatus.COMPLETE);
            redisCacheService.put(downId, fileDownInfo);

        } catch (Exception ex) {
            ex.printStackTrace();
            fileDownInfo.setFileDownStatus(FileDownStatus.FAIL);
            fileDownInfo.setMessage(ex.getMessage());
            redisCacheService.put(downId, fileDownInfo);
        }

    }

    private void writeCsv(Connection conn) {

        String downId = this.fileDownInfo.getDownloadId();

        try {

            fileDownInfo.setMessage(IndicatorConstant.WARNING_MEASS);

            String fileName = this.buildCsvFileName();
            CsvWriter csvWriter = new CsvWriter(fileName, ',', Charset.forName("UTF-8"));

            String sql = this.fileDownInfo.getSql();
            PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Integer.MIN_VALUE);
            ResultSet rs = ps.executeQuery();

            List<String> titleList = null;
            if (tuple.isMeasureDetail()) {
                titleList = this.getTiltes(rs);
            } else {
                titleList = this.getTiltes();
            }

            this.setCsvTitle(csvWriter, titleList);

            List<String> columnLabelList = this.getColumnLabels();
            BigInteger progress = BigInteger.valueOf(0);
            BigInteger zero = BigInteger.valueOf(0);
            //每写入flushNumber行后，输出到对象状态。
            BigInteger flushNumber = BigInteger.valueOf(1000);
            String[] rows = new String[titleList.size()];

            boolean measDetail = this.fileDownInfo.isMeasDetail();
            if (measDetail) {
                columnLabelList = new LinkedList<String>();
                int len = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= len; i++) {
                    String label = rs.getMetaData().getColumnLabel(i);
                    columnLabelList.add(label);
                }
            }

            while (rs.next()) {

                progress = progress.add(BigInteger.ONE);
                int idx = 0;

                for (String columnLabel : columnLabelList) {
                    String value = rs.getString(columnLabel);
                    rows[idx++] = formartNull(value);
                }

                if (progress.mod(flushNumber).equals(zero)) {
                    fileDownInfo.setProgress(progress);
                    redisCacheService.put(downId, fileDownInfo);
                    //异步导出大文件优先级别不高，每写flushNumber后,cpu释放200ms，防止cpu拉满。
                    TimeUnit.MILLISECONDS.sleep(80);
                }

                csvWriter.writeRecord(rows, true);
                csvWriter.flush();//刷新数据
            }

            fileDownInfo.setProgress(progress);
            csvWriter.close();

            //发送BOS上
            String fileUpKey = BosFileServiceImpl.writeBos(fileName);
            fileDownInfo.setFileKey(fileUpKey);
            fileDownInfo.setFileName(fileName);
            fileDownInfo.setFileDownStatus(FileDownStatus.COMPLETE);
            redisCacheService.put(downId, fileDownInfo);

        } catch (Exception ex) {
            ex.printStackTrace();
            fileDownInfo.setFileDownStatus(FileDownStatus.FAIL);
            fileDownInfo.setMessage(ex.getMessage());
            redisCacheService.put(downId, fileDownInfo);
        }
    }

    private static String buildXlsFileName() {

        SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");
        String name = "Adhoc";
        String fileName = name + "_" + UUID.randomUUID().toString() + "_" + dateFormat.format(new Date()) + ".xlsx";

        return fileName;

    }

    private String buildCsvFileName() {

        SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");
        String name = "Adhoc";
        String fileName = name + "_" + UUID.randomUUID().toString() + "_" + dateFormat.format(new Date()) + ".csv";

        return fileName;

    }

    private static final String formartNull(String value) {
        if (StringUtil.isEmpty(value)) {
            value = "-";
        }
        return value;
    }

    private void writeXls(Connection conn) {

        String sql = this.fileDownInfo.getSql();
        String downId = this.fileDownInfo.getDownloadId();

        try {

            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = null;
            rs = pstmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<HashMap<String, String>> valueMapList = new ArrayList<HashMap<String, String>>();
            while (rs.next()) {

                HashMap<String, String> rowMap = new HashMap<String, String>();
                for (int i = 1; i <= columnCount; i++) {
                    String label = metaData.getColumnLabel(i);
                    String value = formartNull(rs.getString(label));
                    rowMap.put(label, value);
                }

                valueMapList.add(rowMap);

            }

            XSSFWorkbook workbook = new XSSFWorkbook();

            String fileName = this.buildXlsFileName();
            //创建一个Excel表单,参数为sheet的名字
            XSSFSheet sheet = workbook.createSheet(fileName);
            //创建表头,并获得表头数据
            this.setXlsTitle(workbook, sheet, rs);
            List<String> columnLabelList = this.getColumnLabels();

            boolean measDetail = this.fileDownInfo.isMeasDetail();
            if (measDetail) {
                columnLabelList = new LinkedList<String>();
                int len = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= len; i++) {
                    String label = rs.getMetaData().getColumnLabel(i);
                    columnLabelList.add(label);
                }
            }

            //新增数据行，并且设置单元格数据
            int rowNum = 1;

            XSSFCellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("#,##0.00"));
            numberStyle.setAlignment(HorizontalAlignment.LEFT);

            for (HashMap<String, String> rowMap : valueMapList) {
                XSSFRow row = sheet.createRow(rowNum);
                for (int idx = 0; idx < columnLabelList.size(); idx++) {
                    String columnLabel = columnLabelList.get(idx);
                    String value = rowMap.get(columnLabel);

                    boolean isNumber = StringUtil.isNumber(value);
                    XSSFCell cell = row.createCell(idx);

                    if (isNumber) {
                        cell.setCellStyle(numberStyle);
                    }

                    row.createCell(idx).setCellValue(value);
                }
                rowNum++;

            }

            FileOutputStream output = new FileOutputStream(fileName);

            workbook.write(output);
            output.flush();
            output.close();

            fileDownInfo.setFileName(fileName);

            //发送BOS上
            String fileUpKey = BosFileServiceImpl.writeBos(fileName);
            fileDownInfo.setFileKey(fileUpKey);
            fileDownInfo.setFileDownStatus(FileDownStatus.COMPLETE);
            redisCacheService.put(downId, fileDownInfo);

        } catch (Exception ex) {
            ex.printStackTrace();
            fileDownInfo.setFileDownStatus(FileDownStatus.FAIL);
            fileDownInfo.setMessage(ex.getMessage());
            redisCacheService.put(downId, fileDownInfo);
        }

    }

    private static CellStyle createCellStyle(XSSFWorkbook workbook, Matrix.Cell dataCell, Map<MemberType, CellStyle> styleMap) {

        MemberType memberType = dataCell.getMemberType();
        CellStyle cellStyle = styleMap.get(memberType);
        if (null != cellStyle) {
            return cellStyle;
        }

        // 第四步，创建单元格，并设置值表头 设置表头居中
        CellStyle style = workbook.createCellStyle();

        //设置样式对齐方式：水平\垂直居中
        if (MemberType.DIMENSION.equals(memberType) || MemberType.MEASURE.equals(memberType) || MemberType.MEASURE_GROUP.equals(memberType)) {
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        if (MemberType.MEASURE_VALUE.equals(memberType)) {
            style.setAlignment(HorizontalAlignment.RIGHT);
        }

        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // 单元格边框
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);

        // 文本自动换行
        style.setWrapText(true);

        styleMap.put(memberType, style);

        return style;

    }

    public static String writePivotXls(Matrix matrix) throws Exception {
        return writePivotXls(matrix, new HashMap<>());
    }

    /**
     * 获取同环比数据
     * @param dataCell
     * @return
     */
    private static String getRatioInfo(Matrix.Cell dataCell) {

        StringBuilder ratioInfoBuilder = new StringBuilder();
        List<Matrix.Cell.Ratio> ratioList = dataCell.getRatioList();
        if (null != ratioList && ratioList.size() > 0) {

            for (Matrix.Cell.Ratio ratio : ratioList) {
                //同环比类型
                RatioType ratioType = ratio.getRatioType();
                String ratioValue = ratio.getRatio();
                String value = ratio.getValue();

                String ratioName = RatioType.MONTHONMONTH.equals(ratioType) ? "环比" : "同比";
                ratioInfoBuilder.append(" ").append(ratioName).append(":").append(ratioValue);//.append(":").append(value);

            }

        }

        return ratioInfoBuilder.toString();


    }

    public static String writePivotXls(Matrix matrix, Map<MemberType, CellStyle> styleMap) throws Exception {


        String fileName = buildXlsFileName();

        Map<Integer, Integer> colWidthMap = new HashMap<>();
        XSSFWorkbook workbook = new XSSFWorkbook();
        //创建一个Excel表单,参数为sheet的名字
        XSSFSheet sheet = workbook.createSheet(fileName);
        //创建表头,并获得表头数据
        int rows = matrix.getHeight();
        int cols = matrix.getWidth();

        for (int i = 0; i < rows; i++) {
            XSSFRow row = sheet.createRow(i);
            for (int j = 0; j < cols; j++) {

                Matrix.Cell dataCell = matrix.get(i, j);
                XSSFCell cell = row.createCell(j);
                String value = dataCell.getValue();
                if (IndicatorConstant.BI_NULL.equalsIgnoreCase(value)) {

                    value = " ";
                    CubeMember cubeMember = dataCell.getCubeMember();
                    if (null != cubeMember && null != cubeMember.getDimension()) {

                        Dimension dimension = cubeMember.getDimension();

                        String dimCode = dimension.getCode();
                        String dimName = dimension.getName();
                        String code = cubeMember.getCode();
//                        value = "IsNull[DC:" + dimCode + ";DN:" + dimName + ";CD:" + code + "]";
                        value = " ";
                    }

                }

                value += getRatioInfo(dataCell);

                cell.setCellValue(value);

                Integer width = value.getBytes().length;

                Integer maxWidth = colWidthMap.get(j);
                if (null == maxWidth || maxWidth < width) {
                    colWidthMap.put(j, width);
                }

                CellStyle cellStyle = createCellStyle(workbook, dataCell, styleMap);
                cell.setCellStyle(cellStyle);

            }
        }

        Set<Map.Entry<Integer, Integer>> colWidthSet = colWidthMap.entrySet();
        for (Map.Entry<Integer, Integer> colWidth : colWidthSet) {

            Integer columnNum = colWidth.getKey();
            Integer columnWidth = colWidth.getValue();
            //* 256 适配中文，888适当加宽，留点边距。
            sheet.setColumnWidth(columnNum, columnWidth * 256 + 888);

        }

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {

                Matrix.Cell dataCell = matrix.get(i, j);
                int rowspan = dataCell.getRowspan();
                int colspan = dataCell.getColspan();

                if (rowspan > 1 || colspan > 1) {
                    //如果只有1个单元格，无需合并

                    int firstRow = i;
                    int lastRow = i + rowspan - 1;
                    int firstCol = j;
                    int lastCol = j + colspan - 1;

                    CellRangeAddress region = new CellRangeAddress(firstRow, lastRow, firstCol, lastCol);
                    sheet.addMergedRegion(region);

                }
            }
        }

        FileOutputStream output = new FileOutputStream(fileName);

        workbook.write(output);
        output.flush();
        output.close();

        return fileName;

    }

    /***
     * 设置表头
     */
    private void setCsvTitle(CsvWriter csvWriter, List<String> titleList) throws IOException {
        csvWriter.writeRecord(titleList.toArray(new String[] {}), true);
        csvWriter.flush();//刷新数据
    }

    /***
     * 设置表头
     * @param workbook
     * @param sheet
     */
    private void setXlsTitle(XSSFWorkbook workbook, XSSFSheet sheet, ResultSet rs){
        XSSFRow row = sheet.createRow(0);
        // 设置列宽，setColumnWidth的第二个参数要乘以256，这个参数的单位是1/256个字符宽度
        // sheet.setColumnWidth(0, 10*256);
        // 设置为居中加粗
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);

        XSSFCell cell;

        List<String> titleList = null;
        if (tuple.isMeasureDetail()) {
            titleList = this.getTiltes(rs);
        } else {
            titleList = this.getTiltes();
        }

        for (int idx=0; idx < titleList.size(); idx++) {
            cell = row.createCell(idx);
            cell.setCellValue(titleList.get(idx));
            cell.setCellStyle(style);
        }

    }

    private List<String> getTiltes(ResultSet rs) {

        List<String> titleList = new LinkedList<String>();
        try {

            ResultSetMetaData rsmd = rs.getMetaData();//rs为查询结果集

            int count = rsmd.getColumnCount();
            for(int i = 1; i <= count; i++){
                titleList.add(rsmd.getColumnName(i));//把列名存入向量heads中
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }

        return titleList;

    }

    private List<String> getTiltes() {

        Set<Dimension> choiceDimensionSet = this.fileDownInfo.getChoiceDimensionSet();
        Set<Measure> choiceMeasureSet = this.fileDownInfo.getChoiceMeasureSet();

        List<String> titleList = new LinkedList<String>();
        for (Dimension dimension : choiceDimensionSet) {
            titleList.add(dimension.getName());
        }

        for (Measure measure : choiceMeasureSet) {
            titleList.add(measure.getName());
        }

        return titleList;

    }

    private List<String> getColumnLabels() {

        Set<Dimension> choiceDimensionSet = this.fileDownInfo.getChoiceDimensionSet();
        Set<Measure> choiceMeasureSet = this.fileDownInfo.getChoiceMeasureSet();

        List<String> columnLabelList = new LinkedList<String>();
        for (Dimension dimension : choiceDimensionSet) {
            columnLabelList.add("_d_alis_" + dimension.getName());
        }

        for (Measure measure : choiceMeasureSet) {
            columnLabelList.add("_m_alis_" + measure.getName());
        }

        return columnLabelList;

    }

    private BigInteger getCount(Connection conn) {

        BigInteger cnt = BigInteger.valueOf(0);
        String sql = this.fileDownInfo.getCountSql();
        Statement statement = null;

        try {

            statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            while(rs.next()) {
                cnt =  BigInteger.valueOf(rs.getInt(1));
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return cnt;

    }

    public static void main(String[] args) throws Exception {

        XSSFWorkbook workbook = new XSSFWorkbook();

        String fileName = "测试文件" + UUID.randomUUID() + ".xlsx";
        //创建一个Excel表单,参数为sheet的名字
        XSSFSheet sheet = workbook.createSheet(fileName);
        //创建表头,并获得表头数据

        //新增数据行，并且设置单元格数据
        int rowNum = 0;

        XSSFCellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("#,##0.00"));
        numberStyle.setAlignment(HorizontalAlignment.LEFT);

        XSSFRow row = sheet.createRow(rowNum);
        int idx = 0;
        String value = "123";
        boolean isNumber = StringUtil.isNumber(value);
        XSSFCell cell = row.createCell(idx);

        if (isNumber) {
            cell.setCellStyle(numberStyle);
            cell.setCellValue(Double.valueOf(value));
        } else {
            cell.setCellValue(value);
        }

        rowNum++;

        FileOutputStream output = new FileOutputStream(fileName);

        workbook.write(output);
        output.flush();
        output.close();

    }
}
