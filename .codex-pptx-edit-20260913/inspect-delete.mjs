import { FileBlob, PresentationFile } from "@oai/artifact-tool";
const p=await PresentationFile.importPptx(await FileBlob.load("D:/works/semi-overt-backend/output/个人博客系统项目演示1_第16页修改版.pptx"));
const slide=p.slides.getItem(15);
const image=p.resolve('im/03ylwfe1');
function chain(obj){const out=[];let o=obj;for(let i=0;o&&i<5;i++,o=Object.getPrototypeOf(o)){out.push({i,names:Object.getOwnPropertyNames(o)});}return out;}
console.log(JSON.stringify({image:chain(image),images:chain(slide.images),shapes:chain(slide.shapes)},null,2));
const h=p.help('*',{search:'slide.images.delete|slide.images.remove|image.delete|slide.shapes.delete|shape.delete|remove',include:['index','examples','notes'],maxChars:16000});
console.log(h.ndjson);
