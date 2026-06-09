package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.QueryResult;
import com.graphinsight.indicator.model.QueryResultColumnInfo;
import com.graphinsight.indicator.service.FileSourceService;
import com.graphinsight.indicator.util.StringUtil;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class FileSourceServiceImpl implements FileSourceService {

    @Override
    public void writeSheet(DataSource dataSource, QueryResult data, HttpServletResponse response) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();

        String fileName = this.buildFileName(dataSource);
        //创建一个Excel表单,参数为sheet的名字
        XSSFSheet sheet = workbook.createSheet(fileName);
        //创建表头
        setTitleX(workbook, sheet, data.getInfos());

        XSSFCellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("#,##0.00"));
        numberStyle.setAlignment(HorizontalAlignment.LEFT);

        //新增数据行，并且设置单元格数据
        int rowNum = 1;
        for (List<String> cells : data.getValues()) {
            XSSFRow row = sheet.createRow(rowNum);
            for(int idx = 0; idx < data.getInfos().size(); idx++) {

                String value = cells.get(idx);
                boolean isNumber = StringUtil.isNumber(value);

                XSSFCell cell = row.createCell(idx);

                if (isNumber) {
                    cell.setCellStyle(numberStyle);
                }

                cell.setCellValue(value);

            }
            rowNum++;
        }
        // 清空response
        response.reset();
        // 设置response的Header
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, PUT");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        OutputStream os = new BufferedOutputStream(response.getOutputStream());
        response.setContentType("application/vnd.ms-excel;charset=gb2312;filename=" + fileName);
        // 将excel写入到输出流中
        workbook.write(os);
        os.flush();
        os.close();

    }

    private String buildFileName(DataSource dataSource) {

        String name = dataSource.getName();
        SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");

        if (name == null) {
            name = "Adhoc";
        }

        String fileName = name + "_" + dateFormat.format(new Date()) + ".xlsx";
        return fileName;

    }

    /***
     * 设置表头
     * @param workbook
     * @param sheet
     */
    private void setTitleX(XSSFWorkbook workbook, XSSFSheet sheet, List<QueryResultColumnInfo> titles){
        XSSFRow row = sheet.createRow(0);
        // 设置列宽，setColumnWidth的第二个参数要乘以256，这个参数的单位是1/256个字符宽度
        // sheet.setColumnWidth(0, 10*256);
        // 设置为居中加粗
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);

        XSSFCell cell;
        for (int idx=0; idx < titles.size(); idx++) {
            cell = row.createCell(idx);
            cell.setCellValue(titles.get(idx).getName());
            cell.setCellStyle(style);
        }
    }

}
