import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/xiao/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_98sc7godnpaq21_4ed5/msg/file/2026-06/IT人才盘点分类补充版_保留原分类.xlsx";
const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);

const preview = await workbook.inspect({
  kind: "table",
  range: "IT人才盘点分类!A1:E260",
  include: "values,formulas",
  tableMaxRows: 260,
  tableMaxCols: 5,
});

const data = JSON.parse(preview.ndjson);
for (let i = 0; i < data.values.length; i += 1) {
  const row = data.values[i];
  if (row.some((v) => v !== null && v !== "")) {
    console.log(`${String(i + 1).padStart(3, " ")}\t${row.map((v) => v ?? "").join("\t")}`);
  }
}
