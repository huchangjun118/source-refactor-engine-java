# source-refactor-engine-java

基于规则的源码重构与批量改写引擎。输入是一个源码工程目录和一组改写规则，
输出是改写后的源码与一份可核对的变更清单。整个过程只在本地完成，不依赖任何外部服务。

## 能力概览

- **符号级改写**：基于 JavaParser 符号解析（而非文本替换）执行重命名。引用被改写对象的位置
  全部更新；字符串字面量、注释、同名但被遮蔽的局部变量、无关类型的同名成员都不会被误改。
  改写完成后每个受影响的文件都会被重新解析校验，无法解析则整个任务中止、不落盘。
- **跨文件一致落地**：一次任务涉及的所有文件先暂存再统一提交；任何一步失败都会把已触碰的
  文件恢复到原始字节，绝不留下"一半新一半旧"的工程。每次成功提交都会生成 journal，
  可用 rollback 整体回退到改写前状态。
- **预览即所得**：preview 与 apply 共用同一条规划代码路径，变更清单逐项对应真实改动
  （文件、行列、偏移、改前/改后内容、规则 id）。
- **多规则确定性合并**：规则按 `(order, id)` 排序依次作用；改到同一处的冲突按
  "order 最小者胜出"的确定策略处理，并写入 `conflicts` 清单，绝不悄悄吞掉。
  同一任务重放逐字节一致；重复执行幂等（第二次运行不再产生新差异）。
- **解析失败策略**：`SKIP`（默认）跳过无法解析的文件并在 `skippedFiles` 中给出文件、
  行列与原因，原文件字节保持不动；`FAIL` 直接中止整个任务且不写任何文件。
- **增量与并发**：基于内容哈希 + 符号解析依赖的增量缓存，只重算受规则或输入变化影响的文件，
  增量结果与全量重跑一致；计算阶段并发执行，超时/取消时任务中止且不写任何文件，
  缓存文件始终原子写入，不会损坏。

## 构建与测试

```bash
mvn compile
mvn test
```

启动 HTTP 服务（默认随机端口，可用 `--server.port=8080` 指定）：

```bash
mvn spring-boot:run
```

## REST API

### POST /api/refactor/preview

计算变更计划，不写盘。

### POST /api/refactor/apply

计算与 preview 完全相同的计划并一致落盘，返回 `journalId` 供回滚。

### POST /api/refactor/rollback

```json
{ "projectDir": "/path/to/project", "journalId": "20261006120000-ab12cd34" }
```

把该次任务修改过的所有文件整体恢复为改写前字节，并删除该 journal。

### 请求体（preview / apply）

```json
{
  "projectDir": "/path/to/project",
  "rules": [
    { "kind": "rename-method", "id": "m1", "order": 1,
      "ownerType": "com.acme.Service", "oldName": "find", "newName": "locate" },
    { "kind": "rename-field", "id": "f1", "order": 2,
      "ownerType": "com.acme.Config", "oldName": "count", "newName": "total" },
    { "kind": "rename-type", "id": "t1", "order": 3,
      "qualifiedName": "com.acme.OldService", "newSimpleName": "NewService" }
  ],
  "parseErrorPolicy": "SKIP",
  "concurrency": 8,
  "timeoutSeconds": 300,
  "incremental": true
}
```

## 规则语义

所有规则都是**符号级**的：先按名字粗匹配，再经符号解析确认目标声明确实归属规则指定的
类型，且该声明必须存在于本工程内（JDK/第三方库的方法不会被改，也无法一致地改）。
新名字必须是合法 Java 标识符，否则规则构造即报错。

| kind | 参数 | 语义 |
| --- | --- | --- |
| `rename-method` | `ownerType`, `oldName`, `newName` | 重命名方法声明及全部解析到该声明的调用点与方法引用（`x::find`）。`ownerType` 为空字符串表示工程中任意类型的同名方法。 |
| `rename-field` | `ownerType`, `oldName`, `newName` | 重命名字段声明及全部解析到该字段的访问（含未加 `this.` 的裸引用）。遮蔽该字段的局部变量/参数解析到不同声明，不会被改。 |
| `rename-type` | `qualifiedName`, `newSimpleName` | 重命名类型（类/接口/枚举/record/注解）及其构造器、全部类型引用、注解、import（含静态 import 的类型段）与经类型名的静态成员访问。只改简单名，不改包名，文件不需要移动。 |

公共参数：`id`（任务内唯一，用于变更清单溯源与冲突仲裁）、`order`（整数，越小越先执行，
冲突时越小越优先）。

### 多规则与冲突

- 规则按 `(order, id)` 升序作用于同一批文件。
- 两条规则产生**完全相同**的编辑（同区间同新文本）时自动去重，不算冲突。
- 两条规则产生**区间重叠但内容不同**的编辑时构成冲突：`order` 最小（并列时 `id` 字典序最小）
  的规则胜出，其余编辑被丢弃，并记录到报告的 `conflicts` 中（文件、偏移、涉及规则、
  胜出规则、处理方式）。冲突永远显式报告，不会被静默吞掉。

## 变更清单格式（RefactorReport）

```json
{
  "taskId": "uuid",
  "applied": true,
  "journalId": "20261006120000-ab12cd34",
  "filesScanned": 25,
  "filesChanged": 24,
  "filesReusedFromCache": 0,
  "changes": [
    { "file": "com/acme/Service.java", "line": 3, "column": 17, "offset": 59,
      "oldText": "find", "newText": "locate", "ruleId": "m1" }
  ],
  "conflicts": [
    { "file": "com/acme/A.java", "offset": 45,
      "ruleIds": ["a-rule", "b-rule"], "winnerRuleId": "a-rule",
      "resolution": "kept edit from lowest-order rule 'a-rule', dropped 1 overlapping edit(s)" }
  ],
  "skippedFiles": [
    { "file": "com/acme/Broken.java", "reason": "Parse error...", "line": 3, "column": 21 }
  ]
}
```

- `changes` 按 `(file, offset)` 排序，逐项对应真实落盘的改动；preview 与 apply 的清单一致。
- `file` 为工程相对路径；`line`/`column` 从 1 开始；`offset` 为 0 起的字符偏移。
- `skippedFiles` 中的文件保持原字节不变。
- `journalId` 仅在 apply 且确有改动时返回。

## 配置项

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `parseErrorPolicy` | `SKIP` | `SKIP`：跳过无法解析的文件并记录位置；`FAIL`：整个任务失败且不写盘。 |
| `concurrency` | CPU 核数 | 规则计算的并发度。符号解析内部有全局锁保证线程安全。 |
| `timeoutSeconds` | 300 | 计算阶段超时即取消任务；取消/超时不会留下写了一半的文件或损坏的缓存。 |
| `incremental` | `true` | 启用增量缓存；置 `false` 强制全量重算（结果与增量一致）。 |

## 状态目录与回滚

引擎在工程根目录下维护 `.refactor-engine/`：

- `cache.json`：增量缓存（文件哈希、每文件编辑结果、解析依赖、包成员）。原子写入；
  损坏或缺失时自动退化为全量计算。删除它只会损失性能，不影响正确性。
- `journals/<id>/`：每次 apply 前备份的全部原始文件字节与索引。`rollback` 据此整体恢复。

### 增量失效规则（正确性保证）

满足以下任一条件时文件会被重算，否则复用缓存的编辑结果：

1. 文件自身内容哈希变化，或是新文件；
2. 规则集指纹变化（任何规则参数变化都会触发全量重算）；
3. 同包内有文件增删（包级成员变化会影响未限定名的解析）；
4. 该文件符号解析时触碰过的任一工程文件（含父类型传递闭包）发生变化；
5. 解析曾失败过的文件在工程有任何结构变化时保守重算。

局限：新增一个会遮蔽外部库类型的全新工程类型时，建议删除缓存或 `incremental=false`
全量跑一次。
