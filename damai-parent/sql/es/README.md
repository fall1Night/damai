# ElasticSearch 索引设计说明

> 对应技术文档 §7.3 ElasticSearch 索引设计；mapping 文件见同目录 [`program-mapping.json`](./program-mapping.json)。

## 1. 索引概览

| 项 | 值 | 说明 |
|---|---|---|
| 索引名 | `damai_program` | 节目索引 |
| 数据来源 | MySQL `t_program` 表 | MySQL 为权威写库，ES 为读副本 |
| 主分片 | 3 | 与 Kafka 分区、未来扩容规划对齐 |
| 副本 | 1 | 生产建议 ≥1，保证高可用 |
| 刷新间隔 | 1s | 兼顾搜索实时性与写入吞吐 |
| dynamic | `strict` | 拒绝 mapping 未定义字段，防脏数据污染 |

## 2. 同步策略（技术文档 §7.3）

- **实时同步**：节目发布/上下架 → 发 Kafka `damai-program-sync-es` → ES 消费者更新索引。
- **兜底对账**：定时任务每小时全量比对 MySQL 与 ES，补偿消息丢失导致的不一致。

## 3. 分词器

| 分词器 | 用途 | 说明 |
|---|---|---|
| `ik_max_word`（索引时） | 最细粒度切分 | 提高召回率。例：`周杰伦演唱会` → `周杰伦/演唱会/演出/唱` |
| `ik_smart`（查询时） | 智能切分 | 减少歧义。例：`周杰伦演唱会` → `周杰伦/演唱会` |

> **前置条件**：ES 集群需预装 [IK Analysis Plugin](https://github.com/medcl/elasticsearch-analysis-ik)（版本需与 ES 8.13.4 对应）。

## 4. 字段说明

### 4.1 主键标识

| 字段 | 类型 | 说明 |
|---|---|---|
| `programId` | keyword | 节目 ID（对应 `t_program.id`），精确匹配/按 ID 更新 |

### 4.2 搜索字段（中文分词 text）

| 字段 | 类型 | 说明 |
|---|---|---|
| `title` | text + keyword 子字段 | 节目名称，核心搜索字段；子字段支持精确匹配/聚合 |
| `artist` | text | 艺人/表演团体，支持按艺人搜索 |
| `venue` | text + keyword 子字段 | 场馆名称，支持按场馆搜索；子字段可聚合 |
| `description` | text | 节目介绍，全文检索辅助字段，命中可提升相关度 |

> 搜索场景见 PRD §7.2.3：关键词可为节目名/艺人/场馆，均走 ES 分词 match 查询。

### 4.3 筛选字段（keyword 精确匹配）

| 字段 | 类型 | 说明 |
|---|---|---|
| `typeCode` | keyword | 节目类型编码（关联 `t_program_type.code`），分类浏览筛选 |
| `cityCode` | keyword | 城市编码（关联 `t_region.code`），按地区筛选/聚合 |
| `cityName` | keyword | 城市名称（冗余），便于前端直接展示 |
| `status` | keyword | 节目状态（ProgramStatusEnum），状态筛选 |

### 4.4 范围筛选字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `priceMin` | double | 最低票价，价格区间筛选（range gte） |
| `priceMax` | double | 最高票价，价格区间筛选（range lte） |
| `showStart` | date | 演出开始时间（首场），时间范围筛选 |
| `showEnd` | date | 演出结束时间（末场） |
| `saleStart` | date | 开售时间，筛选即将开售的节目（抢票起跑点） |

### 4.5 排序字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `heat` | integer | 热度值，首页推荐与搜索结果按热度排序依据 |

### 4.6 辅助/系统字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `posterUrl` | keyword（index=false） | 海报图 URL，仅存储不参与搜索（节省索引空间） |
| `createTime` | date | 创建时间，可按上架时间排序 |
| `updateTime` | date | 更新时间，增量同步判断依据 |

## 5. 常用查询示例（供后续 ES Manager 实现参考）

### 5.1 多条件分类浏览（BoolQuery）

```json
{
  "query": {
    "bool": {
      "filter": [
        { "term": { "typeCode": "concert" } },
        { "term": { "cityCode": "beijing" } },
        { "range": { "priceMin": { "gte": 500 } } },
        { "range": { "priceMax": { "lte": 2000 } } },
        { "range": { "showStart": { "gte": "2026-07-01" } } }
      ]
    }
  },
  "sort": [
    { "heat": { "order": "desc" } }
  ]
}
```

### 5.2 智能搜索（多字段 match + 高亮）

```json
{
  "query": {
    "multi_match": {
      "query": "周杰伦",
      "fields": ["title^3", "artist^2", "venue"]
    }
  },
  "highlight": {
    "fields": {
      "title": {},
      "artist": {}
    }
  },
  "sort": [
    { "_score": { "order": "desc" } },
    { "heat": { "order": "desc" } }
  ]
}
```
