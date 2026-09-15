import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";
import {
  AppWindow, ShieldCheck, Route, FileText, ClipboardCheck, Search,
  Image, Bell, RadioTower, Cloud, Network, MessageSquareMore,
  Container, Sparkles, Database, Layers3
} from "lucide";

const workspaceDir = "D:/works/semi-overt-backend";
const SKILL_DIR = "C:/Users/32624/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations";
const sourcePath = "D:/works/semi-overt-backend/output/个人博客系统项目演示1_第16页修改版.pptx";
const mediaDir = "D:/works/semi-overt-backend/.codex-pptx-edit-20260913/media-extract-1";
const buildDir = "D:/works/semi-overt-backend/.codex-pptx-edit-20260913/build-v2";
const stagingDir = "D:/works/semi-overt-backend/.codex-finalizer-pptx16-v2";
const FINAL_PPTX = "D:/works/semi-overt-backend/output/个人博客系统项目演示1_第16页项目校正版.pptx";
const RUNTIME_PYTHON = "C:/Users/32624/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe";
const referenceSha256 = "153061c9a9b454d0543d1f560a4b7a299dfbc8abb16c8fca6961d420515499d9";

await fs.mkdir(buildDir, { recursive: true });
await fs.mkdir(stagingDir, { recursive: true });
await fs.mkdir(path.dirname(FINAL_PPTX), { recursive: true });

const presentation = await PresentationFile.importPptx(await FileBlob.load(sourcePath));
if (presentation.slides.items.length !== 16) throw new Error(`Expected 16 slides, got ${presentation.slides.items.length}`);
const slide = presentation.slides.getItem(15);

// Rebuild only slide 16. The inherited master background stays; all slide-level content,
// including the original Doubao watermark image, is removed.
slide.shapes.deleteAll();
for (const image of [...slide.images.items]) image.delete();

const C = {
  ink: "#252A31",
  muted: "#646A72",
  surface: "#F1F2F4/95",
  row: "#FFFFFF/92",
  border: "#DFE2E6",
  green: "#288E76",
  blue: "#3C78BD",
  purple: "#7654AA",
  softGreen: "#DDF0EA",
  softBlue: "#E1ECF8",
  softPurple: "#EEE7F8",
};
const FONT_CN = "微软雅黑";
const FONT_LATIN = "Arial";

function addText(text, x, y, w, h, opts = {}) {
  const s = slide.shapes.add({
    geometry: "textbox",
    name: opts.name,
    position: { left: x, top: y, width: w, height: h },
    fill: opts.fill ?? "none",
    line: opts.line ?? { style: "solid", fill: "none", width: 0 },
    borderRadius: opts.borderRadius,
  });
  s.text = text;
  s.text.style = {
    typeface: opts.typeface ?? FONT_CN,
    fontSize: opts.fontSize ?? 16,
    bold: opts.bold ?? false,
    color: opts.color ?? C.ink,
    alignment: opts.alignment ?? "left",
    verticalAlignment: opts.verticalAlignment ?? "middle",
    autoFit: opts.autoFit ?? "shrinkText",
    insets: opts.insets ?? { top: 0, right: 0, bottom: 0, left: 0 },
  };
  return s;
}
function iconSvg(iconNode, color = C.ink, strokeWidth = 1.9) {
  const body = iconNode.map(([tag, attrs]) => {
    const at = Object.entries(attrs).map(([k, v]) => `${k}="${String(v)}"`).join(" ");
    return `<${tag} ${at}/>`;
  }).join("");
  return new TextEncoder().encode(`<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`);
}
function addIcon(iconNode, x, y, size, color, alt) {
  return slide.images.add({ blob: iconSvg(iconNode, color), contentType: "image/svg+xml", alt, fit: "contain", position: { left: x, top: y, width: size, height: size } });
}
function addServiceGroup({ x, accent, soft, icon, title, subtitle, items }) {
  slide.shapes.add({
    geometry: "roundRect",
    name: `service-group-${title}`,
    position: { left: x, top: 137, width: 368, height: 327 },
    fill: C.surface,
    line: { style: "solid", fill: "#FFFFFF/80", width: 1 },
    borderRadius: 18,
    shadow: "shadow-sm",
  });
  slide.shapes.add({ geometry: "roundRect", position: { left: x + 20, top: 158, width: 6, height: 53 }, fill: accent, line: { style: "solid", fill: "none", width: 0 }, borderRadius: "rounded-full" });
  slide.shapes.add({ geometry: "ellipse", position: { left: x + 38, top: 156, width: 50, height: 50 }, fill: soft, line: { style: "solid", fill: "none", width: 0 } });
  addIcon(icon, x + 51, 169, 24, accent, `${title}图标`);
  addText(title, x + 102, 153, 230, 34, { fontSize: 22, bold: true });
  addText(subtitle, x + 102, 183, 238, 23, { fontSize: 12.5, color: C.muted });
  const rowY = [224, 294, 364];
  items.forEach((it, idx) => {
    slide.shapes.add({
      geometry: "roundRect",
      name: `module-${it.label}`,
      position: { left: x + 20, top: rowY[idx], width: 328, height: 56 },
      fill: C.row,
      line: { style: "solid", fill: C.border, width: 1 },
      borderRadius: 12,
    });
    slide.shapes.add({ geometry: "ellipse", position: { left: x + 36, top: rowY[idx] + 16, width: 24, height: 24 }, fill: soft, line: { style: "solid", fill: "none", width: 0 } });
    addIcon(it.icon, x + 42, rowY[idx] + 22, 12, accent, `${it.label}图标`);
    addText(it.label, x + 72, rowY[idx] + 7, 247, 25, { fontSize: 15.5, bold: true });
    addText(it.note, x + 72, rowY[idx] + 30, 247, 18, { fontSize: 10.8, color: C.muted });
  });
}

addText("项目模块与技术栈", 55, 32, 555, 70, { fontSize: 44, bold: true });
addText("CODE-VERIFIED  /  以当前 pom.xml · package.json · Docker Compose 为准", 615, 49, 595, 32, { fontSize: 13, color: C.muted, alignment: "right" });
slide.shapes.add({ geometry: "roundRect", position: { left: 57, top: 111, width: 34, height: 4 }, fill: C.ink, line: { style: "solid", fill: "none", width: 0 }, borderRadius: "rounded-full" });
addText("Vue 前端经 Gateway 接入 6 个业务服务，Outbox + RabbitMQ 串联审核、通知与搜索链路", 105, 98, 950, 28, { fontSize: 14, color: C.muted });
addText("校验日期 2026.09.13", 1070, 98, 140, 28, { typeface: FONT_LATIN, fontSize: 11.5, color: C.muted, alignment: "right" });

addServiceGroup({
  x: 56, accent: C.green, soft: C.softGreen, icon: Route,
  title: "访问与身份", subtitle: "前端入口、路由治理与用户事实",
  items: [
    { icon: AppWindow, label: "Vue 前端应用", note: "浏览、创作后台与 AI 润色入口" },
    { icon: Route, label: "gateway-service", note: "路由、JWT 校验、限流与身份头" },
    { icon: ShieldCheck, label: "auth-service", note: "注册、登录、找回密码与用户资料" },
  ],
});
addServiceGroup({
  x: 456, accent: C.blue, soft: C.softBlue, icon: FileText,
  title: "内容主链路", subtitle: "文章真源、审核决策与公开检索",
  items: [
    { icon: FileText, label: "content-service", note: "文章、草稿、提审与 DeepSeek 润色" },
    { icon: ClipboardCheck, label: "review-service", note: "审核待办、决策、日志与投影" },
    { icon: Search, label: "search-service", note: "公开搜索与文章事件消费" },
  ],
});
addServiceGroup({
  x: 856, accent: C.purple, soft: C.softPurple, icon: Layers3,
  title: "基础支撑", subtitle: "媒体、消息投递与事件基础设施",
  items: [
    { icon: Image, label: "file-service", note: "图片上传与静态文件访问" },
    { icon: Bell, label: "notification-service", note: "通知消费、站内通知与投递记录" },
    { icon: RadioTower, label: "platform-events", note: "Outbox、RabbitMQ 拓扑与消费基座" },
  ],
});

addText("技术栈", 57, 503, 125, 33, { fontSize: 22, bold: true });
addText("当前仓库依赖", 57, 535, 125, 20, { fontSize: 11.5, color: C.muted });
addText("后端 · 中间件 · 前端", 57, 559, 125, 20, { fontSize: 11.5, color: C.muted });
addText("版本来自本地配置", 57, 583, 125, 20, { fontSize: 11.5, color: C.muted });

const techs = [
  { label: "Java 17", file: "image16.png", color: "#E94836" },
  { label: "Spring Boot 3.2.3", file: "image15.png", color: "#67B53C" },
  { label: "Spring Cloud 2023.0", icon: Cloud, color: "#3C78BD" },
  { label: "MyBatis+ 3.5.5", file: "image17.png", color: "#CF3030", wide: true },
  { label: "MySQL 8.0", file: "image18.png", color: "#0A6F9C" },
  { label: "Redis 7.2", file: "image19.png", color: "#D72B20" },
  { label: "RabbitMQ 3.13", icon: MessageSquareMore, color: "#F26722" },
  { label: "Nacos 2.3.2", icon: Network, color: "#2675D8" },
  { label: "LangChain4j + DeepSeek", icon: Sparkles, color: "#7654AA" },
  { label: "Vue 3.5", file: "image20.png", color: "#42B883" },
  { label: "TypeScript 5.9", file: "image21.png", color: "#1676BC" },
  { label: "Docker Compose", icon: Container, color: "#1676BC" },
];
const startX = 205, cardW = 164, gap = 8, rowH = 54;
for (let i = 0; i < techs.length; i++) {
  const tech = techs[i];
  const col = i % 6;
  const row = Math.floor(i / 6);
  const x = startX + col * (cardW + gap);
  const y = 503 + row * 64;
  slide.shapes.add({
    geometry: "roundRect", name: `tech-${tech.label}`,
    position: { left: x, top: y, width: cardW, height: rowH },
    fill: "#F4F5F6/96", line: { style: "solid", fill: "#FFFFFF/85", width: 1 },
    borderRadius: 12, shadow: "shadow-sm",
  });
  slide.shapes.add({ geometry: "ellipse", position: { left: x + 10, top: y + 9, width: 36, height: 36 }, fill: "#FFFFFF", line: { style: "solid", fill: "#E4E6E9", width: 1 } });
  if (tech.file) {
    const bytes = new Uint8Array(await fs.readFile(path.join(mediaDir, tech.file)));
    const iw = tech.wide ? 42 : 25;
    const ih = tech.wide ? 19 : 25;
    slide.images.add({ blob: bytes, contentType: "image/png", alt: `${tech.label} 技术栈图标`, fit: "contain", position: { left: x + 28 - iw / 2, top: y + 27 - ih / 2, width: iw, height: ih } });
  } else {
    addIcon(tech.icon, x + 18, y + 17, 20, tech.color, `${tech.label} 技术栈图标`);
  }
  addText(tech.label, x + 54, y + 7, cardW - 62, 40, { typeface: FONT_LATIN, fontSize: tech.label.length > 20 ? 9.4 : 10.8, bold: true, color: C.ink });
  slide.shapes.add({ geometry: "roundRect", position: { left: x + cardW - 7, top: y + 12, width: 3, height: 30 }, fill: tech.color, line: { style: "solid", fill: "none", width: 0 }, borderRadius: "rounded-full" });
}

const preview = await slide.export({ format: "png", scale: 2 });
await fs.writeFile(path.join(buildDir, "slide-16-v2.png"), new Uint8Array(await preview.arrayBuffer()));
const layout = await slide.export({ format: "layout" });
await fs.writeFile(path.join(buildDir, "slide-16-v2.layout.json"), await layout.text(), "utf8");
const montage = await presentation.export({ format: "webp", montage: true, scale: 0.5 });
await fs.writeFile(path.join(buildDir, "montage-v2.webp"), new Uint8Array(await montage.arrayBuffer()));

const candidatePath = path.join(stagingDir, "candidate.pptx");
await (await PresentationFile.exportPptx(presentation)).save(candidatePath);
const { finalizePresentation } = await import(pathToFileURL(path.join(SKILL_DIR, "container_tools/artifact_tool_utils.mjs")).href);
const requirements = { explicitTotalSlideCount: 16, requiredNativeTableOwnerSlides: [], requiredNativeChartOwnerSlides: [], requiredEmbeddedWorkbookChartOwnerSlides: [] };
const fontPolicy = { basis: "reference", families: [FONT_CN, FONT_LATIN], referencePath: sourcePath, referenceSha256 };
const result = await finalizePresentation({
  ...requirements,
  workspaceDir,
  candidatePath,
  finalPath: FINAL_PPTX,
  pythonExecutable: RUNTIME_PYTHON,
  integrityValidatorPath: path.join(SKILL_DIR, "container_tools/inspect_presentation_package_integrity.py"),
  layoutValidatorPath: path.join(SKILL_DIR, "container_tools/inspect_presentation_layout_geometry.py"),
  layoutArgs: ["--expected-slide-size-emu", "12192000,6858000", "--validate-bullet-geometry", "--validate-heading-fit"],
  requiredNativeTableOwnerSlides: [],
  fontPolicy,
  verifyArtifactToolImport: true,
  receiptPath: path.join(stagingDir, `${path.basename(FINAL_PPTX)}.validation.json`),
});
console.log(JSON.stringify({ final: FINAL_PPTX, result }, null, 2));
