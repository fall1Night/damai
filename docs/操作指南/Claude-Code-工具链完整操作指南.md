# Claude Code 工具链完整操作指南

> 本指南按照 **节省上下文 → 优化交互 → 驱动开发 → 外部集成 → 底层语义理解** 的完整操作顺序，系统梳理 Claude Code 全部工具链的安装与使用方法。

---

## 总览速查表

| 分类 | 插件/能力 | 一句话核心作用 | 安装/启用方式 |
|------|-----------|---------------|--------------|
| 省上下文 | RTK | 压缩命令输出 60-90% | 钩子注入，自动生效 |
| 省上下文 | Caveman | 压缩对话内容 ~75% | 注入系统指令，自动生效 |
| 省上下文 | Context-Mode | 沙箱处理大输出，最高省 98% | MCP 配置 |
| 省上下文 | claude-mem | 跨会话记忆，每会话省 2250 Token | `/plugin install claude-mem` |
| 优化插件 | claude-hud | 实时状态栏（上下文/额度） | `/plugin install claude-hud` + `/claude-hud:setup` |
| 优化插件 | karpathy-skills | 4 条编码铁律 | `/plugin install ...` 或下载 CLAUDE.md |
| 实际开发 | Superpowers | 标准化工程流程 | 插件市场安装 |
| 实际开发 | gstack | 23 项技能，多角色切换 | `npx gstack install` |
| 实际开发 | openai-codex | 调用 Codex 审查/开发 | 插件市场安装，需 API Key |
| 辅助 MCP | chrome-devtools | 浏览器自动化调试 | `claude mcp add chrome-devtools ...` |
| 辅助 MCP | gitnexus | 代码知识图谱与依赖分析 | `npx gitnexus analyze` |
| 语言扩展 | LSP（内置） | IDE 级语义跳转/诊断/重构 | 安装对应语言服务器，CC 自动检测启用 |

---

# 第一章：节省上下文（省 Token 四件套）

> **目标**：延长会话寿命，最大化利用有限的上下文窗口。
> **安装优先级**：⭐⭐⭐⭐⭐（建议最先安装，所有后续章节的操作都将受益于本章的优化）

---

## 1.1 RTK — 命令输出压缩

### 核心分工

命令输出压缩工具。拦截 `git status` 等高频命令的输出，过滤注释、空白行和重复日志，按目录聚合结果。**压缩率 60%-90%**，大幅减少命令回显占用的 Token。

### 安装方法

通过 **PreToolUse 钩子** 注入配置：

1. 在项目根目录或全局配置中添加 PreToolUse 钩子
2. 将命令自动重写为 `rtk git status` 等压缩形式
3. 安装完成后 **完全透明自动生效**，无需手动干预

### 使用方式

- 安装后 **自动运行**，无需任何手动操作
- 所有被拦截的命令输出将被自动压缩后返回
- 压缩后的输出保持结构化信息，不影响语义理解

---

## 1.2 Caveman — 对话内容压缩

### 核心分工

对话内容压缩工具。强制 AI "像山顶洞人一样说话"，删除客套话、废话和冗余描述，**仅保留核心技术信息**。节省约 75% 的输出 Token。

### 安装方法

在 `CLAUDE.md` 或通过插件市场注入系统指令：

- 提供 **Lite / Full / Ultra** 三种压缩模式：
  - **Lite**：轻度压缩，保留部分解释
  - **Full**：标准压缩，删除全部客套话
  - **Ultra**：极限压缩，仅输出核心结论

### 使用方式

- 安装后 **自动生效**，AI 的回复风格自动切换
- 可根据需要调整压缩模式（修改配置中的模式参数）
- 如需临时恢复完整输出，可在配置中临时禁用

---

## 1.3 Context-Mode — 大输出沙箱处理

### 核心分工

大输出沙箱处理工具。将 `curl`、读取大文件等操作重定向至沙箱执行，**只回传结论**，不回传原始输出。可将 315KB 压缩至 5.4KB，**节省 98%**。

### 安装方法

作为 **MCP 服务器** 配置：

1. 在 Claude Code 的 MCP 配置文件中添加 Context-Mode 服务器
2. 配置后提供以下工具供 AI 自动调用：
   - `execute` — 在沙箱中执行命令，仅返回结论
   - `fetch_and_index` — 获取并索引内容，仅返回摘要

### 使用方式

- AI 会 **自动判断** 何时需要使用沙箱处理
- 当检测到大文件读取或高输出命令时，自动通过 `execute` 工具在沙箱中执行
- 用户无需手动触发，透明运行

---

## 1.4 claude-mem — 跨会话记忆复利

### 核心分工

跨会话记忆工具。自动捕获每次会话中的 **决策记录** 和 **代码修改记录**，存入本地 SQLite + Chroma 向量数据库。新会话启动时自动注入历史上下文，**每次节省约 2250 Token**。

### 安装方法

```bash
# 从插件市场添加
/plugin marketplace add thedotmack/claude-mem

# 安装插件
/plugin install claude-mem
```

安装完成后 **重启 Claude Code** 即可自动运行。

### 使用方式

- **自动运行**：无需手动操作，后台自动记录决策和修改
- **查看看板**：访问 `http://localhost:37777` 查看记忆看板，浏览历史决策和修改记录
- **手动查询**：可在对话中直接询问历史决策，claude-mem 会自动检索相关记录
- **数据存储**：本地 SQLite + Chroma，数据安全可控

---

# 第二章：优化交互（提升交互与代码质量）

> **目标**：增强 Claude Code 的实时反馈能力和编码规范性。
> **前置依赖**：建议先完成第一章（省上下文四件套）的安装

---

## 2.1 claude-hud — 状态栏仪表盘

### 核心分工

实时状态栏仪表盘。显示以下关键信息：

- 🟢→🔴 **上下文占用百分比**（进度条形式）
- ⏱ **5小时/周配额** 使用情况
- 🌿 **Git 当前分支**
- 🔧 **当前调用的工具名称**

### 安装方法

```bash
# 从插件市场添加
/plugin marketplace add jarrodwatts/claude-hud

# 安装插件
/plugin install claude-hud

# 初始化设置
/claude-hud:setup
```

> **Linux 用户注意**：如遇 `EXDEV` 报错，需设置 `TMPDIR` 环境变量后再执行 setup。

### 使用方式

- 安装 setup 后 **自动显示** 在终端状态栏
- 实时更新，无需刷新
- 上下文占用从绿色渐变到红色，便于判断何时需要压缩或重启会话

---

## 2.2 karpathy-skills — 编码行为指南

### 核心分工

将 Andrej Karpathy 的编码哲学化为 **4 条铁律**：

1. **先思考后编码** — 先理解再动手，不盲目修改
2. **简洁优先** — 用最少的代码完成目标
3. **精准修改** — 只改需要改的地方，不做无关变更
4. **目标驱动** — 始终围绕明确目标推进，不发散

GitHub 62k+ Star，经过大量开发者验证。

### 安装方法

**方式一（插件安装，推荐）**：

```bash
# 从插件市场添加
/plugin marketplace add forrestchang/andrej-karpathy-skills

# 安装指定技能
/plugin install andrej-karpathy-skills@karpathy-skills
```

**方式二（通用方式，下载 CLAUDE.md）**：

```bash
curl -o CLAUDE.md https://raw.githubusercontent.com/forrestchang/andrej-karpathy-skills/main/CLAUDE.md
```

下载后将 `CLAUDE.md` 放置于项目根目录，Claude Code 会自动读取。

### 使用方式

- 安装后 **自动生效**，Claude 的编码行为将遵循 4 条铁律
- 无需手动触发，贯穿整个会话
- 如果使用方式二，确保 `CLAUDE.md` 位于项目根目录

---

# 第三章：驱动开发（核心研发流程）

> **目标**：标准化开发流程，提升多角色协作和代码审查能力。
> **前置依赖**：建议先完成第一、二章的安装

---

## 3.1 Superpowers — 标准化流程管控

### 核心分工

强制推行严肃的工程开发流程，分为 5 个阶段：

```
构思(Brainstorming) → 规格(Spec) → 计划(Plan) → 待办(TD) → 审查(Review)
```

每个阶段都有明确的目标和产出物，防止跳步和随意编码。

### 安装方法

通过 **Claude Code 官方插件市场** 安装：

```bash
# 在插件市场中搜索 Superpowers 并安装
/plugin marketplace search superpowers
/plugin install superpowers
```

> 具体命令视插件市场版本而定，以上为通用安装流程。

### 使用方式

- 安装后在开发流程中 **自动引导** 各阶段
- 使用斜杠命令触发对应阶段（如 `/brainstorm`、`/spec`、`/plan`、`/td`、`/review`）
- 每个阶段完成后需确认产出物，才能进入下一阶段

---

## 3.2 gstack — 多角色创意评估

### 核心分工

内置 **23 项专业技能**，支持切换不同角色视角进行发散思考和方案评估：

- 🏢 **CEO 视角** — 业务价值和优先级判断
- 👨‍💻 **工程师视角** — 技术可行性和实现难度
- 🔍 **审查者视角** — 潜在风险和安全考量
- 以及其他 20 项专业角色...

### 安装方法

```bash
# 一键安装
npx gstack install
```

### 使用方式

- 安装完成后，通过斜杠命令加载对应技能上下文：

```bash
# 示例：加载后端 Go 开发上下文
/use backend-go
```

- 可随时切换角色，获得不同维度的评估
- 适合架构设计、技术选型、方案评审等场景

---

## 3.3 openai-codex — Codex 模型调用

### 核心分工

在 Claude Code 内直接调用 OpenAI Codex 模型，提供三大核心命令：

| 命令 | 功能 | 说明 |
|------|------|------|
| `/codex:review` | 代码审查 | 让 Codex 对当前代码进行审查，发现问题 |
| `/codex:adversarial-review` | 对抗性质疑 | 从反面角度挑战代码设计，找出薄弱环节 |
| `/codex:rescue` | 后台接管调试 | Codex 在后台自动分析并修复问题 |

### 前置条件

- 需要 **ChatGPT 订阅** 或 **OpenAI API Key**
- 插件名：`codex-plugin-cc`

### 安装方法

```bash
# 通过插件市场安装
/plugin marketplace search codex
/plugin install codex-plugin-cc
```

### 使用方式

- 安装后使用 `/codex:` 系列斜杠命令：

```bash
# 代码审查
/codex:review

# 对抗性审查（挑战代码设计）
/codex:adversarial-review

# 后台自动修复
/codex:rescue
```

- 适合需要第二意见或自动化审查的场景

---

# 第四章：外部集成（辅助 MCP）

> **目标**：通过 MCP（Model Context Protocol）扩展 Claude Code 的外部交互能力。
> **前置依赖**：建议先完成前三章的安装

---

## 4.1 chrome-devtools — 浏览器调试/自动化

### 核心分工

基于 Puppeteer，让 Claude 控制 **真实 Chrome 浏览器**，实现：

- 🌐 页面调试与交互
- 📡 网络请求分析与抓包
- 📸 页面截图
- 🖥 控制台日志检查
- 🖱 DOM 操作与自动化测试

### 前置条件

- 需要 **Node.js v20.19+**

### 安装方法

```bash
# 通过 Claude Code MCP 添加
claude mcp add chrome-devtools npx chrome-devtools-mcp@latest
```

### 使用方式

- 安装后 Claude Code 自动获得浏览器控制能力
- 可直接在对话中描述需要浏览器执行的操作：

  > "打开 localhost:8080 并截图"
  > "检查页面上的网络请求"
  > "在控制台执行 document.querySelector(...)"

- Claude 会通过 MCP 工具调用 Puppeteer 完成操作并返回结果

---

## 4.2 gitnexus — 代码知识图谱

### 核心分工

将代码库索引为 **知识图谱**，解析：

- 📦 函数调用关系
- 🏗️ 类继承层次
- 🔗 模块依赖链
- 📍 符号定义与引用

**深度集成 Claude Code**：MCP 工具 + Agent Skills + PreToolUse 钩子，自动增强 `grep` / `glob` 的语义理解能力。

### 安装方法

**方式一（一键索引 + 安装，推荐）**：

```bash
# 在项目根目录执行
npx gitnexus analyze
```

该命令会自动索引代码库并完成集成安装。

**方式二（手动 MCP 添加）**：

```bash
claude mcp add gitnexus -- npx -y @xuansang2770/gitnexus@latest mcp
```

### 使用方式

- 安装后 **自动增强** Claude Code 的代码搜索能力
- `grep` 和 `glob` 的搜索结果将附带语义上下文
- 支持询问代码库级别的结构问题：

  > "这个函数被哪些模块调用？"
  > "画出这个类的完整继承链"
  > "这个模块的下游依赖有哪些？"

- 索引数据本地存储，安全可控

---

# 第五章：底层语义理解（LSP 语言能力扩展）

> **目标**：赋予 Claude Code IDE 级别的语义理解能力。
> **前置依赖**：建议先完成前四章的安装

---

## 5.1 LSP 概述

**LSP（Language Server Protocol）** 是微软提出的开放标准协议，让编辑器与语言服务器通信，提供：

| 能力 | 说明 |
|------|------|
| 🎯 跳转定义 | 按语义跳转到函数/变量的定义位置 |
| 🔍 查找引用 | 精确查找所有引用某符号的位置 |
| ✨ 智能补全 | 基于类型信息的代码补全 |
| 🚨 实时诊断 | 修改时获得实时类型错误和语法警告 |
| ✏️ 安全重命名 | 跨文件的语义级重命名 |

### 核心价值

- **精准定位**：按语义跳转/查引用，不像 grep 文本匹配会误命中同名变量或注释
- **实时诊断 + 类型信息**：让 Claude 在修改/重构代码时获得实时类型错误提示，使重构更可靠
- **强类型语言友好**：对 Java / C# / Go 等强类型语言尤其有用，让 Claude Code 真正"理解"代码结构，而非仅当作文本处理

---

## 5.2 各语言 LSP 服务器安装

Claude Code 已内置 LSP 支持，**无需安装第三方插件**，但需要为项目配置对应的语言服务器。

### Go

```bash
go install golang.org/x/tools/gopls@latest
```

### TypeScript / JavaScript

```bash
npm install -g typescript-language-server
```

### Python

```bash
# 方式一（推荐）
pip install pyright

# 方式二
pip install python-lsp-server
```

### Java

需配置 **Eclipse JDT LS**（通常通过 jdtls 启动）：

```bash
# 下载 jdtls
# 配置启动命令指向 jdtls 可执行文件
# 具体配置因环境而异，参考官方文档
```

### C#

需配置 **OmniSharp** 或 **csharp-ls**：

```bash
# 方式一：OmniSharp
dotnet tool install -g omnisharp

# 方式二：csharp-ls
dotnet tool install -g csharp-ls
```

---

## 5.3 在 Claude Code 中启用与使用

### 自动检测（推荐）

Claude Code 会自动检测项目根目录下的语言配置文件并尝试启动对应 LSP：

| 语言 | 自动检测文件 |
|------|------------|
| Go | `go.mod` |
| TypeScript/JavaScript | `package.json` / `tsconfig.json` |
| Python | `requirements.txt` / `pyproject.toml` |
| Java | `pom.xml` / `build.gradle` |
| C# | `.csproj` / `*.sln` |

### 手动配置

若自动检测未生效，可在项目根目录的 `.claude/settings.json` 或 MCP 配置中指定 LSP 服务器的启动命令。

### 对话中使用

安装启用后，可直接在对话中使用语义指令：

> "跳转到这个函数的定义"
> "查找所有引用此变量的地方"
> "帮我重命名这个接口"
> "这个函数的参数类型是什么？"

Claude 会通过 LSP **精准执行**，不会误匹配同名变量或注释中的文本。

---

## 附录：推荐安装顺序

按照以下顺序安装可获得最佳体验：

```
第一步：省上下文四件套（第一章）
  ├── 1.1 RTK          ← 最先安装，立即减少命令输出
  ├── 1.2 Caveman      ← 安装后所有回复变简洁
  ├── 1.3 Context-Mode ← 处理大输出场景
  └── 1.4 claude-mem   ← 跨会话记忆

第二步：优化交互（第二章）
  ├── 2.1 claude-hud       ← 实时监控上下文用量
  └── 2.2 karpathy-skills  ← 规范编码行为

第三步：驱动开发（第三章）
  ├── 3.1 Superpowers      ← 标准化流程
  ├── 3.2 gstack           ← 多角色评估
  └── 3.3 openai-codex     ← 代码审查

第四步：外部集成（第四章）
  ├── 4.1 chrome-devtools  ← 浏览器自动化
  └── 4.2 gitnexus         ← 代码知识图谱

第五步：底层语义（第五章）
  └── 5.x LSP              ← 根据项目语言安装对应服务器
```

> **提示**：每一步安装完成后，可先在当前会话中验证效果，再进行下一步安装。

---

# 第六章：实战操作方法

> **目标**：针对不同项目场景（新项目 / 已有代码的项目），给出具体的操作步骤与方法。

---

## 6.1 新项目启动流程

从零开始创建项目时，按以下步骤操作：

### 步骤 1：环境初始化

```bash
# 在 Claude Code 中执行 /init，让 CC 生成对项目的理解
/init
```

CC 会分析项目结构、依赖关系、配置文件等，建立对项目的初始认知。

### 步骤 2：需求驱动开发

使用 Superpowers 的 skills 针对需求进行迭代：

```bash
# 构思阶段 — 头脑风暴，发散方案
/brainstorm <你的需求描述>

# 规格阶段 — 产出需求规格文档
/spec <确认后的方案>

# 计划阶段 — 拆解为可执行的实现计划
/plan <规格文档>

# 待办阶段 — 生成具体的开发任务清单
/td <实现计划>

# 审查阶段 — 代码完成后进行审查
/review <目标文件或模块>
```

### 步骤 3：自动化推荐完善

```bash
# 让 CC 推荐项目中可完善的地方
/claude-code-setup:claude-automation-recommender
```

CC 会扫描项目配置、代码结构、测试覆盖率等方面，给出可自动化的改进建议。

### 步骤 4：持续优化

- 根据推荐结果逐步完善项目配置
- 每个重要功能完成后使用 `/review` 进行代码审查
- 定期使用 gstack 切换不同角色视角评估项目状态

---

## 6.2 已有代码的项目接入流程

在已有代码库中接入 Claude Code 时，按以下步骤操作：

### 步骤 1：生成知识图谱

```bash
# 在项目根目录执行，生成代码知识图谱
npx gitnexus analyze
```

生成知识图谱后，CC 提供的 MCP 服务可以让 CC 更准确地关联查找项目中的代码结构、函数调用关系和模块依赖链。

### 步骤 2：项目理解初始化

```bash
# 让 CC 生成对现有项目的理解
/init
```

CC 会结合 gitnexus 知识图谱，快速建立对已有代码库的全面认知。

### 步骤 3：需求迭代开发

使用 Superpowers 的 skills 针对需求迭代：

```bash
# 构思 → 规格 → 计划 → 待办 → 审查
/brainstorm <需求描述>
/spec <确认方案>
/plan <规格文档>
/td <实现计划>
/review <目标文件或模块>
```

### 步骤 4：自动化推荐完善

```bash
# 让 CC 推荐已有项目中可完善的地方
/claude-code-setup:claude-automation-recommender
```

CC 会分析现有代码的配置、结构、测试等方面，给出改进建议。

### ⚠️ 重要提示

> **gitnexus 不会自动更新知识图谱**——在完成大的功能调整后，需要 **手动重新执行** `gitnexus analyze` 来更新知识图谱，确保 CC 的理解与代码保持同步。

```bash
# 大功能调整后，手动更新知识图谱
npx gitnexus analyze
```
