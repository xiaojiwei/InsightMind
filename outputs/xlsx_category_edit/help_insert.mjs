import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/xiao/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_98sc7godnpaq21_4ed5/msg/file/2026-06/IT人才盘点分类补充版_保留原分类.xlsx";
const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const help = await workbook.help("insert column");
console.log(help);
