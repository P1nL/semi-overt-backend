# S4 主代理方向审查

> semi-overt · 文档整理 2026-09-15 · 阶段资料：保留原日期、契约与验收范围；不代表当前版本、整站或生产验收。 [文档中心](../README.md)

日期：2026-09-11。审查和修复已完成；最终通过记录见`s4-acceptance.md`，本文件保留发现问题及纠偏过程。

## 保持的范围

四包子代理均为 gpt-5.6-luna / max；包1仅auth/content及契约，包2仅search，包3仅file，包4仅notification。主代理负责gateway、架构门槛、测试环境和跨包验收。已有S3、S5文件先完整备份，未重置或广泛暂存。生产项目只读/不改动。

## 包1 用户资料/日历/首页

- 确认复用V3 home_article_exposures，无需重复DDL。
- ADMIN资料聚合允许看到私有统计，是单体来源语义；不能放宽S3正文详情的作者/当前分配人权限。
- 要求资料更新只写四个资料字段，避免整实体updateById回写password/role/sessionVersion。
- 要求恢复source分页默认值、limit退路和self/admin 100、visitor 50上限；非法超大数字ID返回404。
- 纠正三年日历边界从“当前时刻减三年”变为三年前当天00:00。
- 要求曝光写入失败不能被catch RuntimeException吞成成功；先成功聚合再记曝光，不重新使用全局last_featured_at。
- 首次真实七服务验收发现访客totalWordCount包含私有文章的缺陷：fixture公开2431字、私有额外498字。保留失败回执 `.runtime/s4/accept-20260911-153719/receipt.json`，要求用数据库条件聚合修复并加回归，不修改期待掩盖泄露。

## 包2 搜索

- 不新建Elasticsearch或跨域写入口，不把现有事件no-op叫独立索引成功。
- SQL排名score必须DESC；实际MySQL验证regex/ESCAPE/分页及多标题清洗。
- 基础数据库查询失败不得返回200空结果；仅可选FULLTEXT失败允许退到真正执行的LIKE。
- 可选索引需检查真实结构，验收显式开启FULLTEXT开关再CREATE/DROP索引，避免只测配置关闭的fallback。
- 卡片需要实际id/author/wordCount等字段，不靠前端默认值掩盖缺失。
- 首次整合编译发现生成的literal `\n`，交回worker修复；后续全产品编译通过不等于测试通过。

## 包3 上传/存储

- Gateway到auth已有持久化UPLOAD/IP/用户预算，禁止file另造预算事实源。
- oldUrl不能证明所有权，不按用户提供URL删除文件；前端注释同步纠正。
- 多构造器Spring组件要求明确@Autowired主构造器，必须实际应用启动验证。
- multipart默认字节限制按source对齐5MiB，请求上限6MiB；实际格式、解码、像素、并发解码上限独立验证。
- Cloudinary允许可选配置及本地协议mock；非loopback明文HTTP不应承载凭据，配置readiness不是线上provider通过。

## 包4 通知/历史

- 不扩大为未读数/已读中心；只恢复单体GET列表limit契约。
- 新通知type ARTICLE_REVIEW和中文内容同步单体；新生成正文有界，旧原文不重写。
- 发现新中文格式比较会拒绝旧S3英文decision重放，要求只识别明确旧S3形状幂等兼容，并拒绝真正author/article/result冲突；不对NULL decision猜绑定。
- EMAIL/PENDING保持诚实，不新增邮件worker、不宣称已发送。
- 子代理报告13模块测试通过，主代理将另跑完整reactor及真实网关消息验收后才按第4包汇报。

## 当前验证边界

原先网关接线26、基线migration/architecture35、后加S4架构12仅是中间证据。最终主代理完整verify为220/0/0/0，最终jar运行S4为92/92、S3为71/71；前端auth14、S3契约15及build通过。

搜索worker未能及时交付可编译的新增回归测试；主代理明确收回main及test写权限并关闭worker，保留其有效实现，亲自完成测试/SQL整合。没有把worker自报当完成：真实500根因是MyBatis将Markdown正则`#{1,6}`解释为参数，改成`[#]{1,6}`后真实mapper/MySQL通过。补了真实三项MySQL检索/排序/FULLTEXT与三项错误语义单测；修复一个长生命周期测试SqlSession缓存旧索引状态的fixture问题。

通知测试使用S5密码变量与统一runner不一致，由主代理修成S4_MYSQL_PASSWORD后重新验证；图片路径拦截实际返回401，验收把401列为拒绝访问成功，而非要求产品改成另一拒绝码。失败日志均保留，未删除测试或关闭安全门槛。
