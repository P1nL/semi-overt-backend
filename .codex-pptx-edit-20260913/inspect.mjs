import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";

const source = "C:/Users/32624/Desktop/个人博客系统项目演示1.pptx";
const out = "D:/works/semi-overt-backend/.codex-pptx-edit-20260913/inspect";
await fs.mkdir(out, { recursive: true });
const presentation = await PresentationFile.importPptx(await FileBlob.load(source));
const all = await presentation.inspect({
  kind: "deck,slide,textbox,shape,image,table,chart,layout",
  include: "id,slide,name,title,text,textPreview,textChars,textLines,bbox,bboxUnit,alt,isPlaceholder,placeholders",
  exclude: "comments,preview",
  maxChars: 100000,
});
await fs.writeFile(path.join(out, "inspect-all.ndjson"), all.ndjson, "utf8");
const slide = presentation.slides.getItem(15);
const preview = await slide.export({ format: "png", scale: 2 });
await fs.writeFile(path.join(out, "slide-16-before.png"), new Uint8Array(await preview.arrayBuffer()));
const layout = await slide.export({ format: "layout" });
await fs.writeFile(path.join(out, "slide-16-before.layout.json"), await layout.text(), "utf8");
for (const n of [14,15,16]) {
  const s = presentation.slides.getItem(n-1);
  const p = await s.export({ format: "png", scale: 1.5 });
  await fs.writeFile(path.join(out, `slide-${n}-before.png`), new Uint8Array(await p.arrayBuffer()));
}
const montage = await presentation.export({ format: "webp", montage: true, scale: 0.5 });
await fs.writeFile(path.join(out, "montage-before.webp"), new Uint8Array(await montage.arrayBuffer()));
console.log(JSON.stringify({slides:presentation.slides.items.length, masters:presentation.masters.items.length, out}, null, 2));
