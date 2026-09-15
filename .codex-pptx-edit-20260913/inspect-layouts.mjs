import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";
const source = "C:/Users/32624/Desktop/个人博客系统项目演示1.pptx";
const out = "D:/works/semi-overt-backend/.codex-pptx-edit-20260913/inspect";
const presentation = await PresentationFile.importPptx(await FileBlob.load(source));
for (const n of [12,15]) {
 const slide=presentation.slides.getItem(n-1);
 const layout=await slide.export({format:'layout'});
 await fs.writeFile(path.join(out,`slide-${n}-before.layout.json`),await layout.text(),'utf8');
}
