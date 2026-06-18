import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";
import fs from "node:fs/promises";

const outputPath = "/Users/xiao/InsightMind/outputs/xlsx_category_edit/IT人才盘点分类补充版_已补充类别.xlsx";
const input = await FileBlob.load(outputPath);
const workbook = await SpreadsheetFile.importXlsx(input);

const preview = await workbook.inspect({
  kind: "table",
  range: "IT人才盘点分类!A1:F25",
  include: "values,formulas",
  tableMaxRows: 25,
  tableMaxCols: 6,
});
console.log(preview.ndjson);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 300 },
  summary: "final formula error scan",
});
console.log(errors.ndjson);

const rendered = await workbook.render({
  sheetName: "IT人才盘点分类",
  range: "A1:F35",
  scale: 2,
});
console.log("render type", rendered?.constructor?.name, Object.keys(rendered ?? {}));
if (typeof rendered?.save === "function") {
  await rendered.save("/Users/xiao/InsightMind/outputs/xlsx_category_edit/render_preview.png");
  console.log("saved render_preview.png");
} else if (typeof rendered?.arrayBuffer === "function") {
  const buffer = Buffer.from(await rendered.arrayBuffer());
  await fs.writeFile("/Users/xiao/InsightMind/outputs/xlsx_category_edit/render_preview.png", buffer);
  console.log("saved render_preview.png");
}
