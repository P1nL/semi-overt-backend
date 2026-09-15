import { FileBlob, PresentationFile } from "@oai/artifact-tool";
const p=await PresentationFile.importPptx(await FileBlob.load("D:/works/semi-overt-backend/output/个人博客系统项目演示1_第16页修改版.pptx"));
const slide=p.slides.getItem(15);
function chain(obj){const out=[];let o=obj;for(let i=0;o&&i<4;i++,o=Object.getPrototypeOf(o)){out.push({i,names:Object.getOwnPropertyNames(o)});}return out;}
const snap=await p.inspect({kind:'image,shape,textbox',maxChars:100000});
console.log(snap.ndjson.split('\n').filter(x=>x.includes('"slide":16')).join('\n'));
console.log(JSON.stringify({imageItems:slide.images.items?.map(x=>({aid:x.aid,name:x.name,frame:x.frame})),imagesProto:chain(slide.images),oneImage:slide.images.items?.[0]?chain(slide.images.items[0]):null,shapesProto:chain(slide.shapes)},null,2));
const h=p.help('*',{search:'slide.images.delete|slide.images.remove|image.delete|slide.shapes.delete|shape.delete|remove image element',include:['index','examples','notes'],maxChars:16000});
console.log(h.ndjson);
