import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/xiao/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_98sc7godnpaq21_4ed5/msg/file/2026-06/IT人才盘点分类补充版_保留原分类.xlsx";
const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const sheet = Array.from(workbook.worksheets)[0];

console.log("sheet keys", Object.keys(sheet));
console.log("sheet proto", Object.getOwnPropertyNames(Object.getPrototypeOf(sheet)));
console.log("workbook keys", Object.keys(workbook));
console.log("workbook proto", Object.getOwnPropertyNames(Object.getPrototypeOf(workbook)));
console.log("worksheet count", workbook.worksheets.length);
const range = sheet.getRange("A1:E10");
console.log("range proto", Object.getOwnPropertyNames(Object.getPrototypeOf(range)));
console.log("range keys", Object.keys(range));
try {
  console.log("range values", range.values);
} catch (err) {
  console.error("values err", err);
}
