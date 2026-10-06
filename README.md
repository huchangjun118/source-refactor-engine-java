# source-refactor-engine-java

基于规则的 Java 源码重构与批量改写引擎。输入是一个源码工程目录和一组改写规则，
输出是改写后的源码和一份可核对的变更清单（change manifest）。整个过程只在本地
完成，不依赖任何外部服务。

## 能力概览

- **符号级改写**：基于 JavaParser + 符号求解器，只改真正的声明与引用；字符串字面量、
  注释、同名但被遮蔽的局部变量不会被误改。改写后每个受影响文件都会重新解析校验。
- **跨文件一致落地**：一次任务的所有文件写入要么全部提交、要么全部不落盘；提交前自动
  备份，已提交的任务可以整体回退到改写前的状态。
- **预览 = 执行**：preview 与 apply 走完全相同的计算路径，变更清单逐项对应真实改动
  （文件、行列、偏移、改前/改后内容）。
- **多规则确定性**：规则按 `(order, id)` 确定的顺序逐条生效；冲突不会被悄悄吞掉，
  按配置的策略处理；同一任务重放得到逐字节一致的结果；重复执行幂等。
- **解析失败策略**：无法解析的文件可按策略跳过（清单中记录文件与出错位置，内容原样
  保留）或让整体失败。
- **增量与并发**：按内容/声明/规则指纹缓存，只重算受影响的文件；增量结果与全量重跑
  一致；取消或超时不会留下写了一半的文件或损坏的缓存。

## 快速开始

```bash
mvn package
java -jar target/source-refactor-engine-java-1.0-SNAPSHOT.jar \
  --refactor.config=refactor.json --refactor.mode=preview   # 只输出变更清单，不写盘
java -jar target/source-refactor-engine-java-1.0-SNAPSHOT.jar \
  --refactor.config=refactor.json --refactor.mode=apply     # 原子提交全部改动
java -jar target/source-refactor-engine-java-1.0-SNAPSHOT.jar \
  --refactor.config=refactor.json --refactor.mode=rollback --refactor.task=<taskId>
```

也可以在代码中直接使用：

```java
RefactorConfig config = RefactorConfig.load(Path.of("refactor.json"));
RefactorEngine engine = new RefactorEngine(config);
TaskResult preview = engine.preview();          // 不写盘
TaskResult applied = engine.apply();            // 原子提交
engine.rollback(applied.manifest().getTaskId());// 整体回退
```

## 配置文件

JSON 格式，示例：

```json
{
  "sourceDir": "/path/to/project",
  "onParseError": "SKIP",
  "onUnresolved": "SKIP_WITH_WARNING",
  "conflictStrategy": "FAIL",
  "parallelism": 8,
  "timeoutMillis": 0,
  "cacheEnabled": true,
  "stateDir": "/path/to/project/.refactor",
  "rules": [
    {
      "id": "rename-service-method",
      "type": "rename-method",
      "order": 1,
      "params": { "owner": "com.acme.Service", "oldName": "oldCall", "newName": "newCall" }
    }
  ]
}
```

### 配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `sourceDir` | （必填） | 源码工程根目录，递归扫描 `*.java`（隐藏目录如 `.git`、`.refactor` 自动排除） |
| `rules` | `[]` | 规则列表，见下 |
| `onParseError` | `SKIP` | `SKIP`：跳过无法解析的文件（清单记录文件+行列，内容原样保留）；`FAIL`：整个任务失败，不写盘 |
| `onUnresolved` | `SKIP_WITH_WARNING` | 符号无法求解时：`SKIP_WITH_WARNING`：保留原样并记录警告；`FAIL`：任务失败 |
| `conflictStrategy` | `FAIL` | 冲突处理策略：`FAIL`：中止任务，不写盘；`FIRST_WINS`：先生效的规则（order 小者优先，其次 id 字典序）胜出，被丢弃的编辑记录进清单 |
| `parallelism` | CPU 核数 | 解析与编辑收集的并发度；结果与并发度无关 |
| `timeoutMillis` | `0`（不限） | 超时即中止，中止前不写任何文件 |
| `cacheEnabled` | `true` | 增量缓存开关 |
| `stateDir` | `<sourceDir>/.refactor` | 缓存、提交日志、求解快照的存放目录 |

### 规则语义

每条规则：`id`（唯一）、`type`、`order`（整数，小的先执行；并列时按 id 字典序并记录警告）、
`params`。规则按顺序逐条生效，后面的规则看到前面规则的改写结果（顺序依赖由此表达）。

| type | 参数 | 语义 |
| --- | --- | --- |
| `rename-method` | `owner`, `oldName`, `newName` | 重命名 `owner`（全限定名）中声明的方法，并更新所有解析到该类型的调用点与方法引用（`x.old()`、`this.old()`、`Type::old`）。其他类型上的同名方法、字符串、注释不受影响 |
| `rename-field` | `owner`, `oldName`, `newName` | 重命名 `owner` 中声明的字段，并更新所有解析到该字段的访问（`this.f`、`obj.f`、裸名 `f`）。遮蔽该名的局部变量/参数解析到自身，不会被改 |
| `rename-type` | `oldFqn`, `newName` | 重命名类型的简单名（包名不变），更新 import、构造器声明与所有解析到该类型的类型引用。**不会重命名磁盘文件** |

无法解析的候选引用按 `onUnresolved` 策略处理，绝不会被“猜着改”。

## 变更清单（change manifest）

预览与正式执行输出完全相同的 JSON 清单；同一任务重放逐字节一致。

```json
{
  "taskId" : "0a411ef1f2ea66cf",
  "changes" : [ {
    "file" : "com/acme/Client.java",
    "line" : 6, "column" : 19, "offset" : 105, "length" : 7,
    "oldText" : "oldCall", "newText" : "newCall",
    "ruleId" : "rename-call"
  } ],
  "skippedFiles" : [ { "file" : "com/acme/Broken.java", "line" : 4, "column" : 24, "reason" : "..." } ],
  "warnings" : [ "..." ],
  "conflicts" : [ { "file" : "...", "message" : "...", "ruleIds" : [ "a", "b" ] } ]
}
```

- `taskId`：由规则集合、输入文件哈希与编辑集合决定的确定性 id；相同输入必然得到相同 id。
- `changes`：每一处改动，含文件、1 起始的行列、字符偏移、长度、改前/改后内容与来源规则。
  行列与偏移对应该次编辑生效时的文件版本（多规则时后一条规则看到的是前面规则的结果）。
- `skippedFiles`：被跳过的不可解析文件及出错位置；这些文件内容原样保留。
- `warnings`：未解析符号、被丢弃的冲突编辑、order 并列等。
- `conflicts`：检测到的编辑冲突（同一规则内的重叠编辑、跨规则改到同一处）。

## 原子提交与回退

- 提交前把每个待改文件的原内容备份到 `<stateDir>/journal/<taskId>/`；
- 每个文件通过“临时文件 + 原子移动”写入，任何一步失败都会用备份把已写文件恢复原状，
  不会留下半新半旧的工程；
- `rollback(taskId)` 用备份把该任务涉及的所有文件整体恢复到改写前状态。

## 增量与缓存

缓存位于 `<stateDir>/cache/`：

- 声明缓存：按文件内容哈希缓存每个文件的类型/方法/字段声明；
- 编辑缓存：按 `规则id + 文件内容哈希 + 全工程声明哈希` 缓存每条规则在每个文件上产生的编辑。

只改方法体不改变声明时，其他文件的编辑缓存全部命中，只有被改文件重新解析；声明变化会
精确失效相关缓存。缓存写入均为原子写，损坏条目按未命中处理，取消/超时不会留下坏缓存。
增量结果与全量重跑一致（有测试保证）。

## 测试

```bash
mvn test
```

覆盖：跨文件引用更新、字符串/注释/遮蔽变量保护、冲突规则（FAIL 与 FIRST_WINS）、
部分失败回滚、提交后整体回退、重复执行幂等、重放逐字节一致、解析失败策略、
增量与全量一致性、并发确定性与取消/超时。
