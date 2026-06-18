# Claude Code 工具链完整操作指南

> 本指南按照 **节省上下文 → 优化交互 → 驱动开发 → 外部集成 → 底层语义理解** 的完整操作顺序，系统梳理 Claude Code 全部工具链的安装与使用方法。

---

## 总览速查表

| 分类 | 插件/能力 | 一句话核心作用 | 安装/启用方式 |
|------|-----------|---------------|--------------|
| 🏗️ 基础设施 | CLAUDE.md | 项目级持久记忆（AI 的项目入职指南） | 项目根目录手动创建 |
| 🏗️ 基础设施 | claude-mem | 跨会话记忆，每会话省 2250 Token | `/plugin install claude-mem` |
| 省上下文 | RTK | 压缩命令输出 60-90% | 钩子注入，自动生效 |
| 省上下文 | Caveman | 压缩对话内容 ~75% | 注入系统指令，自动生效 |
| 省上下文 | Context-Mode | 沙箱处理大输出，最高省 98% | MCP 配置 |
| 优化插件 | claude-hud | 实时状态栏（上下文/额度） | `/plugin install claude-hud` + `/claude-hud:setup` |
| 优化插件 | karpathy-skills | 4 条编码铁律 | `/plugin install ...` 或下载 CLAUDE.md |
| 优化插件 | security-guidance（官方） | 安全编码审查与最佳实践 | `/plugin install security-guidance@claude-plugins-official` |
| 优化插件 | context7（官方） | 实时获取最新文档上下文 | `/plugin install context7@claude-plugins-official` |
| 实际开发 | Superpowers | 标准化工程流程（TDD/Brainstorm/Review） | 插件市场安装，见 3.1 节 |
| 实际开发 | gstack | 23 项技能，多角色切换 | `npx gstack install` |
| 实际开发 | openai-codex | 调用 Codex 审查/开发 | 插件市场安装，需 API Key |
| 辅助 MCP | GitHub MCP | PR/Issue/代码搜索集成 | `claude mcp add github ...` |
| 辅助 MCP | Playwright MCP | 浏览器自动化测试（官方推荐） | `claude mcp add playwright ...` |
| 辅助 MCP | chrome-devtools | 浏览器调试/自动化 | `claude mcp add chrome-devtools ...` |
| 辅助 MCP | gitnexus | 代码知识图谱与依赖分析 | `npx gitnexus analyze` |
| 辅助 MCP | PostgreSQL MCP | 数据库直接查询与管理 | `claude mcp add postgres ...` |
| 辅助 MCP | Slack MCP | Slack 消息读取与发送 | `claude mcp add slack ...` |
| 辅助 MCP | Sentry MCP | 错误追踪与监控集成 | `claude mcp add sentry ...` |
| 语言扩展 | LSP（内置） | IDE 级语义跳转/诊断/重构 | 安装对应语言服务器，CC 自动检测启用 |

---

# 第零章：基础设施（开始之前必须配置）

> **目标**：配置项目级记忆和全局设置，这是所有后续章节的地基。
> **安装优先级**：⭐⭐⭐⭐⭐ **最优先**（本章内容不配置，后续所有插件的发挥都大打折扣）

---

## 0.1 CLAUDE.md — 项目级持久记忆

### 核心分工

`CLAUDE.md` 是 Claude Code 的 **项目入职指南**。放在项目根目录，CC 每次启动会话时自动读取。它不是代码，而是给 AI 的"项目须知"——告诉 CC 这个项目的技术栈、编码规范、目录结构、常用命令等。

### 为什么重要

- ✅ **每次会话自动注入**，无需重复解释项目背景
- ✅ **层级支持**：子目录可放置专属 CLAUDE.md，覆盖或补充根目录配置
- ✅ **节省 Token**：避免每次会话都让 CC 重新探索项目结构

### 创建方法

在项目根目录创建 `CLAUDE.md` 文件，填写以下内容：

```markdown
# 项目名称

## 技术栈
- 语言：Java 17
- 框架：Spring Boot 3.x
- 构建：Maven
- 数据库：MySQL 8.0

## 项目结构
- src/main/java — 源代码
- src/main/resources — 配置文件
- docs/ — 项目文档

## 编码规范
- 使用驼峰命名
- Controller 返回统一响应格式 Result<T>
- Service 层所有 public 方法必须有注释

## 常用命令
- 编译：mvn clean compile
- 测试：mvn test
- 启动：mvn spring-boot:run

## 注意事项
- 不要修改 pom.xml 中的父依赖版本
- 数据库配置在 application-dev.yml 中
```

### 最佳实践

| 实践 | 说明 |
|------|------|
| **保持精简** | 根目录 CLAUDE.md 控制在 200-300 行以内，只写每次会话都需要的信息 |
| **分层管理** | 大项目在子目录放置专属 CLAUDE.md，提供该目录的深度上下文 |
| **只放通用信息** | 不是每次会话都用到的信息不要放进来，避免浪费 Token |
| **定期更新** | 技术栈或规范变更时同步更新 CLAUDE.md |
| **使用 /clear 切换任务** | 新任务开始前执行 `/clear` 清除上下文，让 CC 重新读取 CLAUDE.md |

---

## 0.2 claude-mem — 跨会话记忆复利

### 核心分工

跨会话记忆工具。自动捕获每次会话中的 **决策记录** 和 **代码修改记录**，存入本地 SQLite + Chroma 向量数据库。新会话启动时自动注入历史上下文，**每次节省约 2250 Token**。

> **说明**：claude-mem 横跨基础设施与省上下文两类，是连接「项目记忆」和「会话记忆」的桥梁。

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

# 第一章：节省上下文（省 Token 三件套）

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

# 第二章：优化交互（提升交互与代码质量）

> **目标**：增强 Claude Code 的实时反馈能力和编码规范性。
> **前置依赖**：建议先完成第零章（基础设施）和第一章（省上下文三件套）的配置

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

## 2.3 security-guidance — 安全编码审查（官方插件）

### 核心分工

Anthropic 官方提供的安全编码审查插件。在代码审查过程中自动提供 **安全最佳实践指导**，识别潜在的安全风险（如 SQL 注入、XSS、硬编码密钥等），并给出修复建议。

### 安装方法

```bash
/plugin install security-guidance@claude-plugins-official
```

### 使用方式

- 安装后 **自动生效**，CC 在进行代码审查时会自动附带安全分析
- 无需手动触发
- 适合所有涉及用户输入、认证、数据处理的项目

---

## 2.4 context7 — 实时文档上下文（官方插件）

### 核心分工

Anthropic 官方插件。让 CC 能 **实时获取最新的库/框架文档**，避免因训练数据过期而使用已废弃的 API。尤其在使用较新版本的库时非常关键。

### 安装方法

```bash
/plugin install context7@claude-plugins-official
```

### 使用方式

- 安装后 **自动生效**，当 CC 需要查阅 API 文档时会自动通过 context7 获取最新版本
- 无需手动触发
- 适合使用快速迭代库（如 React、Spring Boot、Next.js 等）的项目

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

**GitHub 官方仓库**：[obra/Superpowers](https://github.com/obra/Superpowers)

```bash
# 从 Claude Code 插件市场安装
/plugin marketplace add obra/superpowers
/plugin install superpowers
```

> **注意**：Superpowers 目前在社区中反响很好，但也存在一些灵活性争议。如果觉得流程过重，可以只用其中的 brainstorm 和 review 两个 skill。

### 使用方式

- 安装后在开发流程中 **自动引导** 各阶段
- 使用斜杠命令触发对应阶段：

| 命令 | 阶段 | 说明 |
|------|------|------|
| `/brainstorm` | 构思 | 头脑风暴，发散方案 |
| `/spec` | 规格 | 产出需求规格文档 |
| `/plan` | 计划 | 拆解为可执行的实现计划 |
| `/td` | 待办 | 生成具体的开发任务清单 |
| `/review` | 审查 | 代码审查，包含 TDD 验证 |

- 支持子代理（subagent）驱动的代码审查和 TDD（测试驱动开发）
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

## 4.3 GitHub MCP — 代码仓库深度集成

### 核心分工

让 CC 直接与 GitHub 仓库交互，无需离开终端即可：

- 📝 创建 / 管理 Issue 和 Pull Request
- 🔍 搜索代码、仓库、用户
- 📊 查看 PR 审查状态和 CI/CD 结果
- 🌿 分支管理与合并操作

### 安装方法

```bash
# 需要先设置 GitHub Token 环境变量
export GITHUB_PERSONAL_ACCESS_TOKEN="ghp_your_token"

# 添加 GitHub MCP 服务器
claude mcp add github npx -y @modelcontextprotocol/server-github
```

### 使用方式

- 安装后在对话中直接操作 GitHub：

  > "帮我创建一个 issue：登录页面在 Safari 下布局错乱"
  > "查看最近的 PR 列表"
  > "搜索项目中所有 TODO 注释"

- 所有操作通过 MCP 工具完成，无需手动打开浏览器

---

## 4.4 Playwright MCP — 浏览器自动化测试（官方推荐）

### 核心分工

Anthropic 官方推荐的浏览器自动化方案。基于 Playwright（而非 Puppeteer），功能更强大：

- ✅ **跨浏览器**：支持 Chrome / Firefox / WebKit
- 🧪 **自动测试**：生成端到端测试用例
- 📸 **截图 & 录屏**：自动截图对比
- 🔗 **网络拦截**：Mock API 响应，测试边界情况

### 安装方法

```bash
# 添加 Playwright MCP 服务器
claude mcp add playwright npx -y @anthropic/create-mcp-server-playwright
```

### 使用方式

- 安装后可直接在对话中描述测试需求：

  > "帮我写一个用户登录的端到端测试"
  > "打开首页并截图对比 UI 变更"
  > "Mock 掉支付 API，测试下单流程的异常处理"

- CC 会自动生成 Playwright 测试脚本并执行

---

## 4.5 PostgreSQL MCP — 数据库直接查询与管理

### 核心分工

让 CC 直接连接和操作 PostgreSQL 数据库，无需手动切换到数据库客户端：

- 📊 执行 SQL 查询并分析结果
- 🏗️ 查看表结构和索引信息
- 📝 生成 DDL / DML 语句
- 🔍 诊断慢查询和性能问题

### 安装方法

```bash
# 需要先配置数据库连接字符串
export DATABASE_URL="postgresql://user:password@localhost:5432/mydb"

# 添加 PostgreSQL MCP 服务器
claude mcp add postgres npx -y @modelcontextprotocol/server-postgres
```

### 使用方式

- 安装后在对话中直接查询数据库：

  > "查看 users 表的结构"
  > "找出最近 7 天注册的用户数"
  > "分析 orders 表的索引使用情况"

- 适合需要频繁查看数据场景，避免在终端和数据库客户端间来回切换

---

## 4.6 Slack MCP — 团队沟通集成

### 核心分工

让 CC 读取和发送 Slack 消息，实现与团队的直接沟通：

- 📨 读取频道消息历史
- 📝 发送消息到指定频道
- 🔍 搜索团队对话记录
- 👤 查看 @提及和通知

### 安装方法

```bash
# 需要先设置 Slack Bot Token
export SLACK_BOT_TOKEN="xoxb-your-token"

# 添加 Slack MCP 服务器
claude mcp add slack npx -y @modelcontextprotocol/server-slack
```

### 使用方式

- 安装后在对话中操作 Slack：

  > "查看 #dev 频道最近的消息"
  > "把测试结果发到 #ci-cd 频道"
  > "搜索关于登录 bug 的讨论记录"

---

## 4.7 Sentry MCP — 错误追踪与监控

### 核心分工

让 CC 直接接入 Sentry 错误追踪系统，快速定位线上问题：

- 🐛 查看最新的错误和异常
- 📊 分析错误趋势和影响范围
- 🔗 关联错误到具体代码行
- 📝 创建 issue 并关联错误

### 安装方法

```bash
# 需要先设置 Sentry Token
export SENTRY_AUTH_TOKEN="your-sentry-token"

# 添加 Sentry MCP 服务器
claude mcp add sentry npx -y @modelcontextprotocol/server-sentry
```

### 使用方式

- 安装后在对话中直接查看错误：

  > "最近一小时有哪些未处理的错误？"
  > "查看 Error #1234 的完整堆栈信息"
  > "分析这个错误影响了多少用户"

---

## 4.8 其他常用 MCP 服务器（按需安装）

以下 MCP 服务器在特定场景下非常有用，按需安装即可：

| MCP 服务器 | 适用场景 | 安装命令 |
|-----------|---------|---------|
| **Linear** | 项目管理（类似 Jira） | `claude mcp add linear npx -y @modelcontextprotocol/server-linear` |
| **Figma** | 设计稿查看与标注提取 | `claude mcp add figma npx -y @anthropic/figma-mcp` |
| **Supabase** | Supabase 数据库与 Auth | `claude mcp add supabase npx -y @modelcontextprotocol/server-supabase` |
| **Filesystem** | 安全的文件系统访问 | `claude mcp add filesystem npx -y @modelcontextprotocol/server-filesystem` |
| **Brave Search** | 让 CC 联网搜索最新信息 | `claude mcp add brave-search npx -y @modelcontextprotocol/server-brave-search` |
| **Memory** | Claude 官方知识图谱记忆 | `claude mcp add memory npx -y @modelcontextprotocol/server-memory` |

> **提示**：MCP 服务器安装数量不宜过多，建议只安装当前项目实际需要的。每个 MCP 服务器都会占用一定的启动时间和上下文空间。

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

## 附录 A：推荐安装顺序

按照以下顺序安装可获得最佳体验：

```
第零步：基础设施（第零章）— 最优先！
  ├── 0.1 CLAUDE.md         ← 创建项目记忆文件，CC 每次会话自动读取
  └── 0.2 claude-mem        ← 跨会话记忆，自动记录决策

第一步：省上下文三件套（第一章）
  ├── 1.1 RTK              ← 压缩命令输出 60-90%
  ├── 1.2 Caveman          ← 压缩对话内容 ~75%
  └── 1.3 Context-Mode     ← 沙箱处理大输出，省 98%

第二步：优化交互（第二章）
  ├── 2.1 claude-hud           ← 实时监控上下文用量
  ├── 2.2 karpathy-skills      ← 规范编码行为
  ├── 2.3 security-guidance    ← 安全编码审查（官方）
  └── 2.4 context7             ← 实时文档上下文（官方）

第三步：驱动开发（第三章）
  ├── 3.1 Superpowers         ← 标准化流程（TDD/Brainstorm/Review）
  ├── 3.2 gstack              ← 多角色评估
  └── 3.3 openai-codex        ← 代码审查（需 API Key）

第四步：外部集成（第四章）— 按需选择
  ├── 4.1 GitHub MCP          ← 代码仓库集成（几乎所有项目都需要）
  ├── 4.2 Playwright MCP      ← 浏览器自动化测试
  ├── 4.3 chrome-devtools     ← 浏览器调试
  ├── 4.4 gitnexus            ← 代码知识图谱
  ├── 4.5 PostgreSQL MCP      ← 数据库查询（有数据库的项目）
  ├── 4.6 Slack MCP           ← 团队沟通（需要的话）
  ├── 4.7 Sentry MCP          ← 错误追踪（需要的话）
  └── 4.8 其他 MCP            ← 按需安装

第五步：底层语义（第五章）
  └── 5.x LSP                 ← 根据项目语言安装对应服务器
```

> **提示**：
> - 第零步到第二步建议 **全部安装**，属于通用优化
> - 第三步按需选择，Superpowers 对规范化开发很有帮助
> - 第四步 **只安装项目实际需要的 MCP**，避免拖慢启动速度
> - 每一步安装完成后，可先在当前会话中验证效果，再进行下一步

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

---

# 第七章：最佳实践与常用技巧

> **目标**：汇总社区验证的高频使用技巧，帮助你更快上手 Claude Code。
> **来源**：综合 [Reddit 25 条技巧](https://www.reddit.com/r/ClaudeAI/comments/1qgccgs/25_claude_code_tips_from_11_months_of_intense_use/)、[GitHub 43 条技巧](https://github.com/ykdojo/claude-code-tips)、[Ultimate Claude Code Guide](https://dev.to/holasoymalva/the-ultimate-claude-code-guide-every-hidden-trick-hack-and-power-feature-you-need-to-know-2l45) 等社区经验。

---

## 7.1 会话管理技巧

| 技巧 | 说明 | 操作 |
|------|------|------|
| **先 Plan 再 Code** | 最重要的一条规则。让 CC 先分析再动手，避免返工 | 对话开始时先描述需求，等 CC 给出方案后再确认执行 |
| **使用 /clear 切换任务** | 新任务开始前清除上下文，释放空间 | 输入 `/clear` |
| **慎用 /compact** | 只在上下文真的不够时使用，会丢失细节 | 输入 `/compact` |
| **及时分支新会话** | 长会话效率递减，复杂任务考虑多会话并行 | 开新终端窗口 |
| **写-测-查循环** | 写代码 → 运行 → 检查输出 → 修正，循环推进 | 让 CC 写完代码后立即运行测试 |

---

## 7.2 CLAUDE.md 进阶用法

### 层级式 CLAUDE.md

```
project/
├── CLAUDE.md              ← 全局信息（技术栈、规范、常用命令）
├── src/
│   ├── CLAUDE.md          ← 源代码目录专属上下文
│   ├── controller/
│   │   └── CLAUDE.md      ← Controller 层规范
│   └── service/
│       └── CLAUDE.md      ← Service 层规范
├── docs/
│   └── CLAUDE.md          ← 文档相关上下文
└── tests/
    └── CLAUDE.md          ← 测试相关上下文
```

### CLAUDE.md 推荐内容模板

```markdown
# 项目名称

## 技术栈
- 语言、框架、构建工具、数据库等

## 编码规范
- 命名规则、注释要求、代码风格

## 常用命令
- 编译、测试、启动、部署命令

## 项目结构
- 关键目录说明

## 注意事项
- 已知问题、禁止修改的文件、依赖版本锁定等

## Git 规范
- 分支命名、Commit 格式、PR 流程
```

> **核心原则**：根目录 CLAUDE.md 控制在 **200-300 行**以内，只写每次会话都需要的信息。

---

## 7.3 Hooks 钩子进阶用法

Claude Code 的 Hooks 系统可以在工具调用的前后自动执行自定义逻辑。

### PreToolUse 钩子（工具调用前拦截）

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "echo '即将执行 Bash 命令'"
          }
        ]
      }
    ]
  }
}
```

### 常见钩子用途

| 用途 | 钩子类型 | 说明 |
|------|---------|------|
| **命令输出压缩** | PreToolUse | 拦截 Bash 工具调用，压缩输出（RTK 的原理） |
| **安全护栏** | PreToolUse | 阻止危险命令（如 `rm -rf`、`DROP TABLE`） |
| **自动格式化** | PostToolUse | 文件修改后自动运行格式化工具 |
| **自动测试** | PostToolUse | 代码修改后自动运行相关测试 |
| **Git 自动提交** | PostToolUse | 修改完成后自动暂存并提交 |

---

## 7.4 50+ 官方插件探索

Claude Code 内置 **50+ 官方插件**，存储在 `~/.claude/plugins/` 目录下。除了本文档推荐的插件外，你可以探索更多：

### 如何浏览全部官方插件

```bash
# 查看已安装的插件
/plugin list

# 搜索官方插件市场
/plugin marketplace search <关键词>

# 安装任何官方插件
/plugin install <plugin-name>@claude-plugins-official

# 查看本地插件目录
ls ~/.claude/plugins/
```

### 社区高评价官方插件

| 插件 | 说明 | 安装命令 |
|------|------|---------|
| **typescript-lsp** | TypeScript/JSX 代码智能（诊断/跳转/引用） | `/plugin install typescript-lsp@claude-plugins-official` |
| **security-guidance** | 安全编码审查 | `/plugin install security-guidance@claude-plugins-official` |
| **context7** | 实时最新文档上下文 | `/plugin install context7@claude-plugins-official` |
| **playwright** | 浏览器自动化测试 | `/plugin install playwright@claude-plugins-official` |

> **注意**：typescript-lsp 在 CC v2.1.63 的 macOS arm64 上有已知 bug（竞态条件），可关注 [GitHub Issue #29858](https://github.com/anthropics/claude-code/issues/29858) 获取修复进展。

---

## 7.5 常用斜杠命令速查

| 命令 | 功能 | 说明 |
|------|------|------|
| `/init` | 初始化项目理解 | 让 CC 分析项目并建立认知 |
| `/clear` | 清除上下文 | 新任务开始前使用 |
| `/compact` | 压缩上下文 | 上下文不足时使用（慎用） |
| `/review` | 代码审查 | 审查指定文件或模块 |
| `/brainstorm` | 构思 | Superpowers 头脑风暴 |
| `/spec` | 规格 | Superpowers 需求规格 |
| `/plan` | 计划 | Superpowers 实现计划 |
| `/td` | 待办 | Superpowers 任务清单 |
| `/codex:review` | Codex 审查 | OpenAI Codex 代码审查 |
| `/codex:adversarial-review` | 对抗审查 | Codex 挑战代码设计 |
| `/codex:rescue` | 自动修复 | Codex 后台接管修复 |
| `/plugin list` | 查看已装插件 | 查看当前安装的插件列表 |
| `/plugin install <name>` | 安装插件 | 从市场安装新插件 |
| `/use <skill>` | 加载技能 | gstack 切换角色上下文 |

---

## 7.6 高效对话技巧

### 如何给 CC 下达更好的指令

| ❌ 低效指令 | ✅ 高效指令 | 区别 |
|------------|-----------|------|
| "优化这个代码" | "优化 UserService 的 findById 方法，目标是将响应时间从 200ms 降到 50ms 以内" | 具体到方法名和量化目标 |
| "写个登录功能" | "在 UserController 中添加 /login POST 接口，参数为 username/password，返回 JWT Token，使用 Spring Security" | 明确技术栈和接口规范 |
| "修复这个 bug" | "OrderService 的 calculateTotal 方法在折扣为 0 时抛出 NPE，需要处理 null 安全情况，并补充单元测试" | 说明 bug 原因、期望行为和测试要求 |

### 提示词黄金法则

1. **具体到文件/方法** — 告诉 CC 操作的精确位置
2. **说明期望行为** — 不要只说"优化"，要说优化成什么样
3. **附带约束条件** — "不要修改 pom.xml"、"保持向后兼容"
4. **要求测试** — "写完代码后补充单元测试"
5. **分步执行** — 大任务拆成小步骤，每步确认后再继续

---

## 附录 B：参考资源

### 官方资源

| 资源 | 链接 |
|------|------|
| Claude Code 官方文档 | [code.claude.com/docs](https://code.claude.com/docs/en/discover-plugins) |
| Claude Code GitHub | [github.com/anthropics/claude-code](https://github.com/anthropics/claude-code) |
| Superpowers GitHub | [github.com/obra/Superpowers](https://github.com/obra/Superpowers) |
| Claude Code Skills 官方文档 | [platform.claude.com/docs](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices) |

### 社区精选

| 资源 | 链接 |
|------|------|
| Awesome Claude Code Plugins | [github.com/ComposioHQ/awesome-claude-plugins](https://github.com/ComposioHQ/awesome-claude-plugins) |
| Claude Code 插件市场目录 | [claudemarketplaces.com](https://claudemarketplaces.com/) |
| MCP Server 排行榜 | [mcpmarket.com/leaderboards](https://mcpmarket.com/leaderboards) |
| 400+ MCP Server 精选列表 | [github.com/tolkonepiu/best-of-mcp-servers](https://github.com/tolkonepiu/best-of-mcp-servers) |
| 25 条 CC 使用技巧（Reddit） | [reddit.com/r/ClaudeAI](https://www.reddit.com/r/ClaudeAI/comments/1qgccgs/25_claude_code_tips_from_11_months_of_intense_use/) |
| 43 条 CC 技巧（GitHub） | [github.com/ykdojo/claude-code-tips](https://github.com/ykdojo/claude-code-tips) |
| Claude Code 终极指南 | [dev.to/holasoymalva](https://dev.to/holasoymalva/the-ultimate-claude-code-guide-every-hidden-trick-hack-and-power-feature-you-need-to-know-2l45) |
| Claude Code Hooks 深入解析 | [joseparreogarcia.substack.com](https://joseparreogarcia.substack.com/p/claude-code-hooks-explained-the-missing) |
| CC 高级最佳实践 | [smartscope.blog](https://smartscope.blog/en/generative-ai/claude/claude-code-best-practices-advanced-2026/) |
