# archive 文档目录说明

这个目录现在同时承载两类内容：

- [backend-understanding](./backend-understanding/README.md)：沿用旧路径的当前逐文件详细讲解
- [distributed-refactor](./distributed-refactor/README.md)：真正的历史改造资料

这样处理的目的很直接：

- 旧的 `backend-understanding` 路径已经被频繁引用，直接复用路径可以避免链接继续漂移
- 但这组内容已经不再是单体时代的历史讲解，而是当前多模块微服务仓库的文件级导读
- 真正需要保留历史语境的内容，只放在 `distributed-refactor`

如果你的目标是理解当前仓库、接手后端开发或准备上线，建议按这个顺序阅读：

1. [项目总览](../01-overview/01-project-overview.md)
2. [后端主题导读](../02-backend-guide/01-api-and-auth.md)
3. [运行与交付](../03-runtime-and-delivery/01-local-development.md)
4. [逐文件详细讲解](./backend-understanding/README.md)

如果你的目标是追溯“为什么会拆成现在这样”，再看：

- [历史改造背景](./distributed-refactor/README.md)
