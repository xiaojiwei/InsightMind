package com.graphinsight.indicator.controller;

import cn.hutool.core.io.resource.Resource;
import cn.hutool.core.io.resource.UrlResource;
import com.alibaba.druid.util.StringUtils;
import com.google.common.collect.Lists;
import com.graphinsight.indicator.annotation.CurrentUser;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.SpaceEmployee;
import com.graphinsight.indicator.service.UserService;
import lombok.extern.slf4j.Slf4j;
import net.bull.javamelody.internal.common.LOG;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;



@RestController
@Slf4j
@RequestMapping("/Excel")
public class ExcelController {
    @Autowired
    UserService userService;

    @PostMapping("/upload")
    public Response uploadExcel(@RequestParam("file") MultipartFile file) {
        try {
            log.info("开始校验文件");
            // 检查文件是否为空
            if (file.isEmpty()) {
                Response.error("上传的文件为空");
            }
            // 获取文件名和文件内容以便进行处理
            String fileName = file.getOriginalFilename();
            String fileExtension = fileName.substring(fileName.lastIndexOf("."));
            if (!fileExtension.equalsIgnoreCase(".xls") && !fileExtension.equalsIgnoreCase(".xlsx")) {
                return Response.error("文件类型不可选择");
            }
            List<String> email = parseExcelFile(file);
            if (("文件内容超过100行").equals(email.get(0))) {
                return Response.error("文件内容超过100行");
            }
            if (("使用模版文件上传").equals(email.get(0))) {
                return Response.error("使用模版文件上传");
            }
            List<SpaceEmployee> spaceEmployees = parseEmail(email);

            return Response.ok(spaceEmployees);
        } catch (Exception e) {
            log.info("文件上传失败: " + e.getMessage());
            // 异常处理
            return Response.error("文件上传失败");
        }
    }

    @GetMapping("/api/downloadFile")
    public void downloadExcel(HttpServletResponse response) throws IOException {
        log.info("创建文件模版");
        // 创建一个新的工作簿
        Workbook workbook = new XSSFWorkbook();

        // 创建工作表
        Sheet sheet = workbook.createSheet("Sheet1");

        // 创建数据行
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("邮箱名称");

        // 添加数据行
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("xxx@graphinsight.com");

        String filename = "用户上传模板.xlsx";
        filename = new String(filename.getBytes("UTF-8"), "ISO-8859-1");

        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        // 将工作簿写入响应流
        workbook.write(response.getOutputStream());
        workbook.close();
        log.info("创建文件模版结束");
//        // 获取要下载的 Excel 文件
//        File excelFile = new File("file.xlsx"); // 替换为实际的 Excel 文件路径
//
//        // 设置响应头
//        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//        response.setHeader("Content-Disposition", "attachment; filename=" + excelFile.getName());
//        response.setContentLength((int) excelFile.length());
//
//        try (FileInputStream fis = new FileInputStream(excelFile)) {
//            // 将 Excel 文件内容写入响应输出流
//            byte[] buffer = new byte[1024];
//            int bytesRead;
//            while ((bytesRead = fis.read(buffer)) != -1) {
//                response.getOutputStream().write(buffer, 0, bytesRead);
//            }
//            response.getOutputStream().flush();
//        } catch (IOException e) {
//            // 处理异常情况
//            e.printStackTrace();
//        }
    }


    private List<String> parseExcelFile(MultipartFile file) {
        List<String> list = new ArrayList<>();
        try (InputStream is = file.getInputStream()) {

            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);
            int value = sheet.getPhysicalNumberOfRows();
            if (value > 100) {
                list.add("文件内容超过100行");
                return list;
            }
            Row headerRow = sheet.getRow(0); // 获取表头行
            if (headerRow == null || !("邮箱名称").equals(headerRow.getCell(0).getStringCellValue())) {
                list.add("使用模版文件上传");
                return list;
            }

            for (Row row : sheet) {
                for (Cell cell : row) {
                    String cellValue = cell.getStringCellValue();
                    if (!StringUtils.isEmpty(cellValue)) {
                        list.add(cellValue);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private List<SpaceEmployee> parseEmail(List<String> email) {
        log.info("开始解析文件");
        List<User> userList = new ArrayList<>();
        List<List<String>> emailList = Lists.partition(email, 100);
        for(List<String> list : emailList ) {
            userList.addAll(userService.getByEmail(list));
        }
        List<SpaceEmployee> spaceEmployeeList = new ArrayList<>();
        for (User user : userList) {
            SpaceEmployee spaceEmployee = new SpaceEmployee();
            spaceEmployee.setName(user.getNickname());
            spaceEmployee.setAvatar(user.getAvatar());
            spaceEmployee.setEmployeeCode(user.getUsername());
            spaceEmployee.setAuthObjectType(AuthObjectType.EMPLOYEE);
            spaceEmployeeList.add(spaceEmployee);
        }
        log.info("解析文件完成");
        return spaceEmployeeList;

    }
}
