import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";
const sourcePath="D:/works/semi-overt-backend/output/个人博客系统项目演示1_第16页修改版.pptx";
const finalPath="D:/works/semi-overt-backend/output/个人博客系统项目演示1_第16页项目校正版.pptx";
const out="D:/works/semi-overt-backend/.codex-pptx-edit-20260913/verify-v2";
await fs.mkdir(path.join(out,'source'),{recursive:true});
await fs.mkdir(path.join(out,'final'),{recursive:true});
const [source,final]=await Promise.all([PresentationFile.importPptx(await FileBlob.load(sourcePath)),PresentationFile.importPptx(await FileBlob.load(finalPath))]);
const diffs=[];
for(let i=0;i<16;i++){
 const [a,b]=await Promise.all([source.slides.getItem(i).export({format:'png',scale:1}),final.slides.getItem(i).export({format:'png',scale:1})]);
 const ab=Buffer.from(await a.arrayBuffer()), bb=Buffer.from(await b.arrayBuffer());
 await fs.writeFile(path.join(out,'source',`slide-${i+1}.png`),ab); await fs.writeFile(path.join(out,'final',`slide-${i+1}.png`),bb);
 const ah=crypto.createHash('sha256').update(ab).digest('hex'), bh=crypto.createHash('sha256').update(bb).digest('hex');
 if(ah!==bh) diffs.push({slide:i+1,sourceSha256:ah,finalSha256:bh});
}
const s16=final.slides.getItem(15);
const layout=await s16.export({format:'layout'}); await fs.writeFile(path.join(out,'slide-16.layout.json'),await layout.text(),'utf8');
const imageSnapshot=await final.inspect({kind:'image',maxChars:100000});
const slide16Images=imageSnapshot.ndjson.split('\n').filter(x=>x.includes('"slide":16')).map(x=>JSON.parse(x));
const watermarkLike=slide16Images.filter(x=>Array.isArray(x.bbox)&&x.bbox[0]>=1100&&x.bbox[1]>=640);
const montage=await final.export({format:'webp',montage:true,scale:0.5}); await fs.writeFile(path.join(out,'final-montage.webp'),Buffer.from(await montage.arrayBuffer()));
const result={sourceSlides:source.slides.items.length,finalSlides:final.slides.items.length,differentRenderedSlides:diffs.map(x=>x.slide),slide16ImageCount:slide16Images.length,watermarkLikeImages:watermarkLike};
await fs.writeFile(path.join(out,'verification.json'),JSON.stringify(result,null,2),'utf8'); console.log(JSON.stringify(result,null,2));
