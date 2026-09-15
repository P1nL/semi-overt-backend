import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";
import {
  BookOpen, Tags, UserRound, FilePenLine, LayoutDashboard,
  Settings2, Sparkles, Braces, Database, Gauge, PenTool
} from "lucide";

const workspaceDir = "D:/works/semi-overt-backend";
const SKILL_DIR = "C:/Users/32624/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations";
const sourcePath = "C:/Users/32624/Desktop/个人博客系统项目演示1.pptx";
const mediaDir = "D:/works/semi-overt-backend/.codex-pptx-edit-20260913/media-extract-1";
const buildDir = "D:/works/semi-overt-backend/.codex-pptx-edit-20260913/build";
const stagingDir = "D:/works/semi-overt-backend/.codex-finalizer-pptx16";
const FINAL_PPTX = "D:/works/semi-overt-backend/output/个人博客系统项目演示1_第16页修改版.pptx";
const RUNTIME_PYTHON = "C:/Users/32624/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe";
const referenceSha256 = "14b38aab59e7c38a352f8dc6fe3f33e34660e5392586b4ab70eda62ed1838e6a";

await fs.mkdir(buildDir, { recursive: true });
await fs.mkdir(stagingDir, { recursive: true });
await fs.mkdir(path.dirname(FINAL_PPTX), { recursive: true });

const presentation = await PresentationFile.importPptx(await FileBlob.load(sourcePath));
if (presentation.slides.items.length !== 16) throw new Error(`Expected 16 slides, got ${presentation.slides.items.length}`);
const slide = presentation.slides.getItem(15);

const C = {
  ink: "#252A31",
  muted: "#63676D",
  line: "#D9DCE1",
  white: "#FFFFFF",
  surface: "#F2F3F5/94",
  row: "#FFFFFF/90",
  green: "#2E8B72",
  blue: "#3F73B5",
  purple: "#7455A6",
  softGreen: "#DDEFE9",
  softBlue: "#E3ECF7",
  softPurple: "#ECE5F6",
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
  return new TextEncoder().encode(
    `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`
  );
}

function addIcon(iconNode, x, y, size, color, alt) {
  return slide.images.add({
    blob: iconSvg(iconNode, color),
    contentType: "image/svg+xml",
    alt,
    fit: "contain",
    position: { left: x, top: y, width: size, height: size },
  });
}

function addModuleGroup({ x, accent, soft, icon, title, subtitle, items }) {
  const panel = slide.shapes.add({
    geometry: "roundRect",
    name: `module-${title}`,
    position: { left: x, top: 137, width: 368, height: 336 },
    fill: C.surface,
    line: { style: "solid", fill: "#FFFFFF/70", width: 1 },
    borderRadius: 18,
    shadow: "shadow-sm",
  });
  const accentBar = slide.shapes.add({
    geometry: "roundRect",
    position: { left: x + 20, top: 160, width: 6, height: 54 },
    fill: accent,
    line: { style: "solid", fill: "none", width: 0 },
    borderRadius: "rounded-full",
  });
  const iconBubble = slide.shapes.add({
    geometry: "ellipse",
    position: { left: x + 38, top: 157, width: 50, height: 50 },
    fill: soft,
    line: { style: "solid", fill: "none", width: 0 },
  });
  addIcon(icon, x + 51, 170, 24, accent, `${title}图标`);
  addText(title, x + 102, 154, 225, 35, { fontSize: 22, bold: true, color: C.ink });
  addText(subtitle, x + 102, 185, 235, 24, { fontSize: 12.5, color: C.muted });

  const rowY = [229, 299, 369];
  items.forEach((it, idx) => {
    slide.shapes.add({
      geometry: "roundRect",
      position: { left: x + 20, top: rowY[idx], width: 328, height: 56 },
      fill: C.row,
      line: { style: "solid", fill: "#E4E6EA/90", width: 1 },
      borderRadius: 12,
    });
    const dot = slide.shapes.add({
      geometry: "ellipse",
      position: { left: x + 36, top: rowY[idx] + 16, width: 24, height: 24 },
      fill: soft,
      line: { style: "solid", fill: "none", width: 0 },
    });
    addIcon(it.icon, x + 42, rowY[idx] + 22, 12, accent, `${it.label}图标`);
    addText(it.label, x + 72, rowY[idx] + 9, 245, 26, { fontSize: 16, bold: true, color: C.ink });
    addText(it.note, x + 72, rowY[idx] + 31, 245, 17, { fontSize: 11.5, color: C.muted });
  });
  return panel;
}

// Re-purpose the original thank-you textbox as the new title so the edit stays local to slide 16.
const titleShape = presentation.resolve("sh/8z2h8bq1");
titleShape.text = "项目模块与技术栈";
titleShape.position = { left: 55, top: 34, width: 540, height: 70 };
titleShape.fill = "none";
titleShape.line = { style: "solid", fill: "none", width: 0 };
titleShape.text.style = {
  typeface: FONT_CN,
  fontSize: 44,
  bold: true,
  color: C.ink,
  alignment: "left",
  verticalAlignment: "middle",
  autoFit: "shrinkText",
  insets: { top: 0, right: 0, bottom: 0, left: 0 },
};

addText("PROJECT MODULES  /  前后端分离 · RESTful API · AI 智能增强", 610, 50, 600, 32, {
  typeface: FONT_CN, fontSize: 13, color: C.muted, alignment: "right"
});
slide.shapes.add({
  geometry: "roundRect",
  position: { left: 57, top: 112, width: 34, height: 4 },
  fill: C.ink,
  line: { style: "solid", fill: "none", width: 0 },
  borderRadius: "rounded-full",
});
addText("从内容浏览到创作管理，再到智能能力与数据服务，形成完整业务闭环", 105, 99, 850, 28, {
  fontSize: 14, color: C.muted
});

addModuleGroup({
  x: 56,
  accent: C.green,
  soft: C.softGreen,
  icon: BookOpen,
  title: "阅读前台",
  subtitle: "面向访客与注册用户",
  items: [
    { icon: BookOpen, label: "文章浏览", note: "列表、详情与多维排序" },
    { icon: Tags, label: "分类与标签", note: "主题聚合与快速定位" },
    { icon: UserRound, label: "用户中心", note: "注册、登录与资料管理" },
  ],
});
addModuleGroup({
  x: 456,
  accent: C.blue,
  soft: C.softBlue,
  icon: FilePenLine,
  title: "创作后台",
  subtitle: "面向内容维护与运营",
  items: [
    { icon: FilePenLine, label: "文章全生命周期", note: "草稿、发布、编辑与封面" },
    { icon: PenTool, label: "Markdown 编辑", note: "结构化创作与即时渲染" },
    { icon: LayoutDashboard, label: "配置与数据看板", note: "站点配置、上传与统计" },
  ],
});
addModuleGroup({
  x: 856,
  accent: C.purple,
  soft: C.softPurple,
  icon: Sparkles,
  title: "智能与服务",
  subtitle: "面向效率提升与系统支撑",
  items: [
    { icon: Sparkles, label: "DeepSeek 智能润色", note: "改写、重生成与确认应用" },
    { icon: Braces, label: "RESTful 数据接口", note: "JSON 通信与前后端解耦" },
    { icon: Database, label: "缓存与数据持久化", note: "高效访问与稳定存储" },
  ],
});

// Technology strip using the original deck's logo assets, normalized into one visual system.
addText("技术栈", 57, 521, 92, 35, { fontSize: 22, bold: true, color: C.ink });
addText("TECH STACK", 57, 554, 92, 20, { typeface: FONT_LATIN, fontSize: 10.5, bold: true, color: C.muted });
addText("核心框架与工具", 57, 579, 92, 20, { fontSize: 11.5, color: C.muted });

const techs = [
  { label: "Spring Boot", file: "image15.png", accent: "#67B53C" },
  { label: "Java 17", file: "image16.png", accent: "#E94836" },
  { label: "MyBatis+", file: "image17.png", accent: "#CF3030", wide: true },
  { label: "MySQL 8", file: "image18.png", accent: "#0A6F9C" },
  { label: "Redis 7", file: "image19.png", accent: "#D72B20" },
  { label: "Vue 3", file: "image20.png", accent: "#42B883" },
  { label: "TypeScript", file: "image21.png", accent: "#1676BC" },
  { label: "Tiptap", file: "image22.png", accent: C.ink },
  { label: "DeepSeek", icon: Sparkles, accent: C.purple },
];
const startX = 158;
const cardW = 109;
const gap = 9;
for (let i = 0; i < techs.length; i++) {
  const tech = techs[i];
  const x = startX + i * (cardW + gap);
  slide.shapes.add({
    geometry: "roundRect",
    name: `tech-${tech.label}`,
    position: { left: x, top: 512, width: cardW, height: 122 },
    fill: "#F4F5F6/95",
    line: { style: "solid", fill: "#FFFFFF/80", width: 1 },
    borderRadius: 14,
    shadow: "shadow-sm",
  });
  slide.shapes.add({
    geometry: "ellipse",
    position: { left: x + 31.5, top: 525, width: 46, height: 46 },
    fill: "#FFFFFF",
    line: { style: "solid", fill: "#E6E8EB", width: 1 },
  });
  if (tech.file) {
    const bytes = new Uint8Array(await fs.readFile(path.join(mediaDir, tech.file)));
    const iw = tech.wide ? 58 : 34;
    const ih = tech.wide ? 26 : 34;
    slide.images.add({
      blob: bytes,
      contentType: "image/png",
      alt: `${tech.label} 技术栈图标`,
      fit: "contain",
      position: { left: x + (cardW - iw) / 2, top: 531 + (34 - ih) / 2, width: iw, height: ih },
    });
  } else {
    addIcon(tech.icon, x + 42.5, 536, 24, tech.accent, `${tech.label} 技术栈图标`);
  }
  addText(tech.label, x + 5, 580, cardW - 10, 27, {
    typeface: FONT_LATIN, fontSize: 12.5, bold: true, color: C.ink, alignment: "center"
  });
  slide.shapes.add({
    geometry: "roundRect",
    position: { left: x + 40, top: 613, width: 29, height: 3 },
    fill: tech.accent,
    line: { style: "solid", fill: "none", width: 0 },
    borderRadius: "rounded-full",
  });
}

// Keep the original footer/watermark image untouched.
const beforePreview = await slide.export({ format: "png", scale: 2 });
await fs.writeFile(path.join(buildDir, "slide-16-after-author.png"), new Uint8Array(await beforePreview.arrayBuffer()));
const layoutAfter = await slide.export({ format: "layout" });
await fs.writeFile(path.join(buildDir, "slide-16-after-author.layout.json"), await layoutAfter.text(), "utf8");
const montage = await presentation.export({ format: "webp", montage: true, scale: 0.5 });
await fs.writeFile(path.join(buildDir, "montage-after-author.webp"), new Uint8Array(await montage.arrayBuffer()));

const candidatePath = path.join(stagingDir, "candidate.pptx");
await (await PresentationFile.exportPptx(presentation)).save(candidatePath);

const { finalizePresentation } = await import(pathToFileURL(
  path.join(SKILL_DIR, "container_tools/artifact_tool_utils.mjs")
).href);
const requirements = {
  explicitTotalSlideCount: 16,
  requiredNativeTableOwnerSlides: [],
  requiredNativeChartOwnerSlides: [],
  requiredEmbeddedWorkbookChartOwnerSlides: [],
};
const fontPolicy = {
  basis: "reference",
  families: [FONT_CN, FONT_LATIN],
  referencePath: sourcePath,
  referenceSha256,
};
const result = await finalizePresentation({
  ...requirements,
  workspaceDir,
  candidatePath,
  finalPath: FINAL_PPTX,
  pythonExecutable: RUNTIME_PYTHON,
  integrityValidatorPath: path.join(SKILL_DIR, "container_tools/inspect_presentation_package_integrity.py"),
  layoutValidatorPath: path.join(SKILL_DIR, "container_tools/inspect_presentation_layout_geometry.py"),
  layoutArgs: [
    "--expected-slide-size-emu", "12192000,6858000",
    "--validate-bullet-geometry",
    "--validate-heading-fit",
  ],
  requiredNativeTableOwnerSlides: [],
  fontPolicy,
  verifyArtifactToolImport: true,
  receiptPath: path.join(stagingDir, `${path.basename(FINAL_PPTX)}.validation.json`),
});
console.log(JSON.stringify({ final: FINAL_PPTX, candidate: candidatePath, result }, null, 2));

