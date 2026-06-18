import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/xiao/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_98sc7godnpaq21_4ed5/msg/file/2026-06/IT人才盘点分类补充版_保留原分类.xlsx";
const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);

console.log("Sheets:");
for (const sheet of workbook.worksheets) {
  console.log(`- ${sheet.name}`);
}

for (const sheet of workbook.worksheets) {
  console.log(`\n--- ${sheet.name} A1:K40 ---`);
  const preview = await workbook.inspect({
    kind: "table",
    range: `${sheet.name}!A1:K40`,
    include: "values,formulas",
    tableMaxRows: 40,
    tableMaxCols: 11,
  });
  console.log(preview.ndjson);
}
