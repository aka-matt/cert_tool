你是一名资深 Java、JavaFX、应用安全和密码工程师。请在当前代码仓库中设计并实现一个生产质量的桌面 GUI 工具，暂定名称为：

# Cert Tool

该工具用于离线读取、解析、检查和转换 JKS、BCFKS，以及它们的 Base64 编码形式。

请不要只生成演示代码或单文件原型。必须采用清晰架构、测试驱动开发、安全默认值、可维护的模块边界，并生成能够构建、测试和运行的完整工程。

---

# 1. 技术栈

使用：

* Java 17 LTS
* JavaFX
* AtlantaFX
* Maven
* JUnit
* AssertJ
* Mockito，仅在确有必要时使用
* TestFX，用于少量关键 UI 测试
* Bouncy Castle APIs
* Bouncy Castle FIPS Java Provider，用于 BCFKS 和 FIPS Provider 相关功能
* SLF4J
* Logback
* Jackson，用于规则配置和应用设置

除非存在明确理由，不要引入 Spring、Jakarta EE、数据库或 Web 服务。

程序必须完全离线工作。不得上传 KeyStore、证书、密码、私钥、密钥元数据或分析结果。

在正式选择依赖版本前，检查各依赖的当前稳定版本、许可证和 Java 17 兼容性。锁定依赖版本，不允许使用动态版本。

---

# 2. 核心安全原则

该程序处理敏感密钥材料，必须遵守：

1. 不在日志中输出：

   * Store Password
   * Key Password
   * Private Key
   * Secret Key
   * 完整 Base64 KeyStore
   * 完整 KeyStore 二进制
   * 用户粘贴的敏感输入

2. 密码在业务层使用 `char[]`，使用完成后尽可能调用 `Arrays.fill()` 清零。

3. 不把密码写入配置文件、最近文件列表、崩溃报告或异常消息。

4. 不创建未经用户明确要求的临时明文文件。

5. Base64 输入应优先在内存中解码。

6. 所有输出文件使用安全的原子写入流程：

   * 写入同目录临时文件；
   * 完成后重新加载并验证；
   * 验证通过后原子移动到目标路径；
   * 失败时删除临时文件。

7. 转换操作不能修改源文件。

8. 对输入设置合理大小限制，默认最大 100 MB，并允许在设置中调整。

9. UI 中显示密码时必须默认为掩码，不提供自动保存密码功能。

10. 错误消息必须有用，但不能泄露密钥材料。

---

# 3. 输入模型

程序支持以下四种用户可见输入形式：

1. Binary JKS
2. Binary BCFKS
3. Base64 JKS
4. Base64 BCFKS

内部不要把它们建模为四个完全独立的格式。使用两个维度：

```java
enum KeyStoreContainerType {
    JKS,
    BCFKS
}

enum ContentEncoding {
    BINARY,
    BASE64
}
```

输入来源包括：

* 系统文件选择器选择二进制文件
* 系统文件选择器选择 Base64 文本文件
* 弹出多行文本框粘贴 Base64
* 文件拖放
* 最近打开文件，只保存路径，不保存密码
* 可选：从剪贴板粘贴 Base64

Base64 解析应支持：

* 标准 Base64
* MIME Base64，包括换行
* 首尾空白
* 可选 PEM 风格包装，例如自定义的 BEGIN/END 行
* 对非法字符、截断内容和空内容给出明确错误

不要仅依据扩展名判断格式。

自动检测流程：

1. 获取解码后的字节；
2. 按候选格式尝试安全加载；
3. 不把“密码错误”和“格式错误”武断地混为一谈；
4. 检测不确定时让用户选择 JKS 或 BCFKS；
5. UI 中显示最终采用的格式和 Provider。

---

# 4. KeyStore 密码流程

加载流程必须支持：

* Store Password
* 私钥条目 Key Password
* Store Password 与 Key Password 相同
* 不同 Alias 使用不同 Key Password
* TrustStore 没有私钥密码
* 用户取消密码输入
* 密码错误后有限次数重试
* 用户选择跳过无法解密的私钥条目，但仍检查公开证书信息

设计 `PasswordProvider` 抽象，不要让底层解析服务直接依赖 JavaFX 对话框。

示例：

```java
public interface PasswordProvider {
    char[] requestStorePassword(StorePasswordRequest request);
    char[] requestEntryPassword(EntryPasswordRequest request);
}
```

测试中必须能够使用假 PasswordProvider。

---

# 5. 条目和证书解析

解析所有 Alias，并识别：

* TrustedCertificateEntry
* PrivateKeyEntry
* SecretKeyEntry
* Unknown / Unreadable Entry

每个条目的领域模型至少包括：

* alias
* entry type
* creation date
* certificate chain
* certificate count
* key algorithm
* key size
* provider
* readable state
* warnings
* errors

对于每张 X.509 证书，解析：

* Subject DN
* Issuer DN
* Serial Number，十六进制和十进制
* X.509 Version
* Valid From
* Valid To
* 当前有效状态
* Signature Algorithm
* Signature Algorithm OID
* Public Key Algorithm
* RSA Key Size
* DSA Parameters
* EC Curve Name 或 OID
* Basic Constraints
* Is CA
* Path Length Constraint
* Key Usage
* Extended Key Usage
* Subject Alternative Names
* Issuer Alternative Names
* Subject Key Identifier
* Authority Key Identifier
* Certificate Policies
* CRL Distribution Points
* Authority Information Access
* SHA-256 Fingerprint
* SHA-1 Fingerprint
* 是否自签名
* 自签名验证结果
* PEM 文本
* Critical Extension OIDs
* Non-critical Extension OIDs
* 未识别的 Critical Extensions

解析扩展时，应优先使用可靠 API，不要通过脆弱的字符串切割解析 ASN.1。

一个条目包含证书链时，显示整条链，并执行基本链检查：

* 每一级 Issuer/Subject 是否衔接
* 下级证书签名能否由上级公钥验证
* 每张证书是否在有效期内
* 中间 CA 是否具有 Basic Constraints
* CA Key Usage 是否允许证书签名
* 根证书是否自签名
* 链顺序是否异常

不要将上述检查描述为完整 PKIX 在线验证，因为程序默认不访问网络，也不自动下载 CRL 或 OCSP 信息。

---

# 6. FIPS 评估的正确语义

功能名称必须使用：

* FIPS Compatibility Assessment
* FIPS Readiness Assessment
* FIPS 兼容性评估

不得把结果称为：

* FIPS Certification
* Official FIPS Validation
* NIST Certified
* 正式认证结论

程序必须始终显示免责声明：

“本工具执行静态兼容性评估。FIPS 140-2/140-3 验证适用于特定密码模块、版本、运行模式和操作环境。该报告不构成 NIST、CMVP、认证实验室或审计机构的正式认证结论。”

评估结果使用：

```java
enum AssessmentStatus {
    PASS,
    WARNING,
    FAIL,
    NOT_ASSESSABLE,
    NOT_APPLICABLE
}
```

每个检查结果必须包含：

```java
record AssessmentFinding(
    String ruleId,
    String title,
    AssessmentStatus status,
    Severity severity,
    String summary,
    String evidence,
    String remediation,
    List<String> references
) {}
```

不要只给出单个绿色或红色图标。必须提供证据、原因、规则编号和修复建议。

---

# 7. FIPS 规则引擎

建立独立的、可测试的规则引擎。规则不能散落在 Controller 中。

至少分为：

* Container Rules
* Entry Rules
* Certificate Rules
* Algorithm Rules
* Runtime Provider Rules
* Conversion Rules

规则通过版本化 JSON 配置加载，内置默认规则，同时允许用户导入自定义规则。

规则配置应包含：

* schema version
* profile id
* profile name
* target standard
* effective date
* allowed algorithms
* disallowed algorithms
* minimum key sizes
* allowed curves
* SHA-1 handling policy
* expiration handling
* unknown algorithm handling
* JKS private-key handling
* required runtime provider
* approved-only mode requirement
* references
* rule severity

内置至少三个 Profile：

1. `FIPS_140_2_LEGACY_ASSESSMENT`
2. `FIPS_140_3_ASSESSMENT`
3. `CUSTOM`

不得在没有可靠规则依据时声称某个证书“符合 FIPS”。

评估至少检查：

## 容器检查

* JKS 或 BCFKS
* Binary 或 Base64
* 是否包含私钥
* JKS 中是否包含私钥
* BCFKS 是否由 FIPS Provider 加载
* 是否能够在目标 Provider 下重新加载
* 完整性保护是否成功

## 算法检查

* MD2
* MD5
* SHA-1 签名
* SHA-2 系列
* SHA-3 系列
* RSA 密钥长度
* DSA 参数长度
* EC 曲线/OID
* EdDSA、X25519 等算法的策略状态
* 未知算法
* 对称密钥算法及长度
* 证书签名算法与公钥算法分别判断

## 证书健康检查

* 已过期
* 尚未生效
* 即将过期
* 自签名
* 链不完整
* 链签名失败
* CA Basic Constraints 缺失
* Key Usage 不匹配
* 未识别 Critical Extension

## 运行时检查

显示并评估：

* Java Runtime Version
* OS
* Security Providers 列表
* 实际 KeyStore Provider
* BC Provider 是否存在
* BCFIPS Provider 是否存在
* Provider 名称和版本
* Approved-Only Mode 状态
* 实际执行检查和转换时使用的 Provider

普通 Bouncy Castle Provider 与 Bouncy Castle FIPS Provider 必须严格区分。

如果无法可靠确认 Approved-Only Mode，结果必须是 `NOT_ASSESSABLE` 或 `WARNING`，不得猜测。

---

# 8. 转换功能

支持以下任意组合：

* JKS Binary → JKS Binary
* JKS Binary → JKS Base64
* JKS Binary → BCFKS Binary
* JKS Binary → BCFKS Base64
* JKS Base64 → 以上四种
* BCFKS Binary → 以上四种
* BCFKS Base64 → 以上四种

转换向导至少包含：

## 第一步：源

* 源格式
* 源编码
* 源路径或内存输入
* 源 Store Password
* 检测结果

## 第二步：内容

显示：

* Alias
* Entry Type
* 是否选择导出
* 证书链长度
* Key Algorithm
* 风险状态

允许用户选择要转换的条目。

## 第三步：目标

* JKS 或 BCFKS
* Binary 或 Base64
* 目标路径
* 目标 Store Password
* Base64 是否换行
* Base64 行宽，默认 64 或 76
* 是否添加包装头尾
* Alias 冲突策略
* 是否覆盖文件

## 第四步：兼容性预检

转换前显示：

* 无法转换的条目
* 需要重新输入密码的条目
* 目标格式不支持的条目
* FIPS Profile 下的警告
* 数据损失风险

## 第五步：执行和验证

* 创建目标 KeyStore
* 复制条目
* 保留 Alias
* 保留证书链
* 写入临时文件
* 重新加载目标文件
* 对比 Alias、条目数、证书指纹和证书链
* 成功后原子替换目标文件
* 生成转换报告

同格式 Binary 与 Base64 之间转换时，不应无意义地重新生成 KeyStore 内容；允许只进行编码或解码，但仍应验证解码后的 KeyStore。

如果从 BCFKS 转为 JKS 会降低安全属性或不满足所选 Profile，必须醒目标记。

---

# 9. 导出功能

支持：

* 导出单张证书为 PEM
* 导出单张证书为 DER
* 导出完整证书链为 PEM
* 复制 SHA-256 指纹
* 复制证书摘要
* 导出检查报告为 JSON
* 导出检查报告为 HTML
* 导出检查报告为 Markdown

默认禁止导出私钥。

本版本不要实现私钥明文导出。

报告中不得包含密码、私钥或秘密密钥内容。

JSON 报告必须有版本化 schema。

---

# 10. JavaFX UI

使用 AtlantaFX 创建现代、专业、紧凑但易读的桌面界面。

必须支持：

* Light
* Dark
* System

提供 `ThemeService`，不要在各个 Controller 中直接修改主题。

System Theme：

* 尽可能检测 Windows、macOS、Linux 桌面主题；
* 检测失败时使用配置的回退主题；
* 在平台支持的情况下监听系统主题变化；
* 用户选择持久化；
* 系统主题实现必须与业务逻辑解耦。

推荐布局：

## 顶部菜单栏

### File

* Open KeyStore
* Open Base64 File
* Paste Base64
* Recent Files
* Close Current
* Exit

### Actions

* Inspect
* Run FIPS Assessment
* Convert
* Export Certificate
* Export Report

### View

* Light
* Dark
* System
* Reset Layout

### Tools

* Runtime Providers
* Rule Profiles
* Settings

### Help

* About
* Security Notice
* FIPS Disclaimer

## 主窗口

使用 `BorderPane`：

* Top：MenuBar + Toolbar
* Left：KeyStore 条目 TreeView 或分组列表
* Center：条目和证书详情
* Right：Findings / Compliance Inspector，可折叠
* Bottom：状态栏

## 左侧条目导航

按以下类别分组：

* Private Keys
* Trusted Certificates
* Secret Keys
* Unreadable Entries

每个节点显示：

* Alias
* 类型图标
* PASS/WARNING/FAIL 状态
* 证书过期提示

## 中间详情

使用 TabPane：

* Overview
* Certificate
* Chain
* Extensions
* PEM
* Findings

Overview 使用卡片和两列属性布局。

证书链使用可选择的链路视图。

PEM 使用等宽字体、只读文本框，并提供复制按钮。

## Compliance 页面

顶部：

* Profile 选择
* Run Assessment
* Export Report

正文：

* 总体摘要卡片
* PASS/WARNING/FAIL 数量
* 可按严重度、Alias、规则分类筛选
* Findings Table
* 右侧详细证据与修复建议

## Convert 页面

使用分步骤 Wizard，不要把所有字段堆在一个页面。

## Runtime 页面

显示：

* JVM
* Java Vendor
* OS
* Providers
* Provider Version
* BCFIPS 状态
* Approved Mode 状态
* 当前默认 KeyStore 类型
* 当前使用的规则 Profile

所有长操作使用 JavaFX `Task` 或后台 Executor。禁止阻塞 JavaFX Application Thread。

支持：

* 进度指示
* 可取消操作
* 操作成功通知
* 可复制错误详情
* 空状态页面
* 键盘导航
* 合理的可访问性文本
* 高 DPI
* 窗口大小和分栏位置持久化

---

# 11. 推荐模块结构（建议）

采用 Maven 多模块项目，建议：

```text
keystore-inspector-fx/
├── pom.xml
├── app/
├── domain/
├── keystore-core/
├── certificate-analysis/
├── compliance-engine/
├── conversion/
├── reporting/
├── platform/
└── test-fixtures/
```

职责：

## domain

只包含不可变领域对象、枚举和值对象，不依赖 JavaFX。

## keystore-core

* 输入检测
* Base64 解码
* KeyStore 加载
* Alias 枚举
* PasswordProvider 抽象
* Provider 选择
* 错误分类

## certificate-analysis

* X.509 解析
* 扩展解析
* 指纹
* 密钥长度
* 曲线识别
* 链检查

## compliance-engine

* Profile
* Rule
* RuleContext
* Finding
* AssessmentReport
* 规则加载
* 默认规则
* FIPS 兼容性评估

## conversion

* ConversionPlan
* Preflight
* Entry Copy
* Alias Conflict Resolution
* Atomic Writer
* Verification

## reporting

* JSON
* HTML
* Markdown
* 报告 schema

## platform

* 系统主题检测
* 文件操作
* 剪贴板
* 最近文件
* 应用设置

## app

* JavaFX Application
* Views
* Controllers/ViewModels
* ThemeService
* Dialogs
* Dependency Composition

UI 层不得直接操作 `KeyStore`、`CertificateFactory` 或 Provider。

Controller 不得包含密码学业务规则。

避免创建全局可变 Singleton。应用 Composition Root 可以集中装配依赖。

---

# 12. 错误模型

不要到处抛出只有字符串的通用异常。

建立明确错误类型，例如：

```java
enum LoadFailureReason {
    FILE_NOT_FOUND,
    FILE_TOO_LARGE,
    FILE_NOT_READABLE,
    EMPTY_INPUT,
    INVALID_BASE64,
    UNSUPPORTED_FORMAT,
    WRONG_STORE_PASSWORD,
    CORRUPTED_KEYSTORE,
    PROVIDER_UNAVAILABLE,
    ENTRY_PASSWORD_REQUIRED,
    WRONG_ENTRY_PASSWORD,
    UNSUPPORTED_ENTRY,
    CANCELLED,
    UNKNOWN
}
```

错误对象包含：

* 用户可读消息
* 技术原因
* 是否可以重试
* 是否需要密码
* 原始异常，仅供受控诊断
* 不敏感的上下文

不要通过解析 Provider 异常文本作为唯一判断依据；在无法确定是密码错误还是损坏时，应明确显示“不确定”。

---

# 13. 测试驱动开发

严格使用 TDD：

1. 先写失败测试；
2. 实现最小代码；
3. 测试通过；
4. 重构；
5. 再进入下一项。

必须覆盖：

## Base64

* 标准 Base64
* MIME 换行
* 空白
* 包装头尾
* 非法字符
* 截断数据
* 空输入
* 超限输入

## KeyStore 加载

* 正确 JKS 密码
* 错误 JKS 密码
* 正确 BCFKS 密码
* 错误 BCFKS 密码
* TrustStore
* PrivateKeyEntry
* SecretKeyEntry
* 多 Alias
* 不同 Entry Password
* 损坏文件
* 错误格式提示
* Provider 缺失

## 证书分析

使用测试夹具生成：

* RSA 2048
* RSA 3072
* 弱 RSA
* EC 证书
* SHA-1 签名证书
* SHA-256 签名证书
* 已过期证书
* 尚未生效证书
* 自签名证书
* 根、中间、叶子证书链
* 链签名错误
* Basic Constraints 错误
* 未识别 Critical Extension

测试证书必须在测试中生成或保存在明确的非生产测试资源中，不要依赖外部网络。

## 规则引擎

* 每条规则独立测试
* Profile 切换
* 未知算法
* 缺失运行时证据
* PASS/WARNING/FAIL 聚合
* 免责声明存在
* 规则配置 schema 验证

## 转换

* JKS → BCFKS
* BCFKS → JKS
* Binary → Base64
* Base64 → Binary
* Alias 保留
* 证书链保留
* 不同 Entry Password
* Alias 冲突
* 不支持条目
* 写入失败
* 验证失败
* 临时文件清理
* 源文件未改变

## 安全测试

* 日志不包含密码
* 日志不包含私钥
* 错误消息不包含完整 Base64
* 配置文件不保存密码
* 报告不包含秘密材料

## UI 测试

只测试关键交互：

* 主题切换
* 打开文件流程
* Base64 粘贴对话框
* 选择 Alias 后显示详情
* 执行 Assessment
* 转换向导校验
* 用户取消密码输入

核心逻辑必须通过普通单元测试覆盖，不要依赖大量脆弱的 UI 自动化测试。

目标：

* 核心模块较高覆盖率
* 规则和转换模块分支覆盖充分
* 所有构建在无网络运行阶段可完成测试
* 测试不得依赖用户机器上的真实证书库

---

# 14. 构建与质量门禁

配置：

* Maven Wrapper
* Spotless
* Checkstyle 或等价静态检查
* SpotBugs
* JaCoCo
* OWASP Dependency-Check，允许针对已确认的误报建立有说明的 suppression
* CycloneDX SBOM
* GitHub Actions

CI 至少运行：

```bash
./mvnw verify
```

并执行：

* 编译
* 单元测试
* 静态检查
* 格式检查
* 覆盖率检查
* 依赖漏洞检查
* SBOM 生成

不得通过跳过测试来让构建通过。

---

# 15. 应用配置

设置文件不得包含秘密信息。

可保存：

* theme：LIGHT / DARK / SYSTEM
* system theme fallback
* window bounds
* divider positions
* recent file paths
* report default directory
* Base64 line width
* default assessment profile
* maximum input size
* expiration warning days
* last selected export format

设置使用版本化 schema，并支持损坏配置回退到默认值。

---

# 16. 文档

生成：

* `README.md`
* `ARCHITECTURE.md`
* `SECURITY.md`
* `FIPS-ASSESSMENT-LIMITATIONS.md`
* `CONTRIBUTING.md`
* `THREAT-MODEL.md`
* `docs/rule-profile-schema.md`
* `docs/report-schema.md`
* `docs/build-and-package.md`

README 应包含：

* 功能
* 截图占位说明
* 构建命令
* 运行命令
* 支持格式
* 安全注意事项
* FIPS 免责声明
* 已知限制

Threat Model 至少覆盖：

* 恶意或损坏 KeyStore
* 超大 Base64 输入
* 密码泄漏
* 日志泄漏
* 临时文件泄漏
* 路径覆盖
* 符号链接攻击
* 恶意证书扩展
* ASN.1 解析异常
* Provider 混淆
* 错误的 FIPS 合规结论
* 依赖供应链风险

---

# 17. 实施顺序

按以下阶段工作，不要一开始就写完整 UI：

## Phase 0：仓库检查和决策记录

* 检查当前仓库
* 确认构建工具和已有代码
* 记录依赖、Provider 和模块设计决策
* 创建 ADR

## Phase 1：领域模型和测试夹具

* 建立领域模型
* 创建测试 KeyStore 和证书生成器
* 建立敏感信息安全测试基础

## Phase 2：输入和加载

* Binary/Base64
* JKS/BCFKS
* PasswordProvider
* 错误分类
* 自动检测

## Phase 3：证书解析

* X.509 字段
* 扩展
* 指纹
* 密钥长度
* 证书链

## Phase 4：规则引擎

* Profile
* Rules
* Findings
* 默认规则
* 免责声明

## Phase 5：转换引擎

* Preflight
* 转换
* 原子写入
* 重载验证
* 转换报告

## Phase 6：报告

* JSON
* HTML
* Markdown

## Phase 7：JavaFX UI

* 主框架
* Inspect
* Compliance
* Convert
* Runtime
* Settings
* ThemeService

每个 Phase 完成后：

1. 运行相关测试；
2. 运行完整 `./mvnw verify`；
3. 修复失败；
4. 更新文档；

---

# 18. 开始工作时的输出要求

在修改代码前，先输出：

1. 对需求的理解；
2. 发现的歧义和风险；
3. 建议的模块架构；
4. Provider 选择策略；
5. FIPS 判断边界；
6. 测试策略；
7. 分阶段实施计划；
8. 预计创建或修改的文件列表。

然后开始 Phase 0 和 Phase 1。

---

# 19. 完成标准

只有同时满足以下条件，才能声称功能完成：

* JKS 和 BCFKS 均可加载
* Binary 和 Base64 均可读取
* 四种形式可以互相转换
* 多 Alias 和不同 Entry Password 得到处理
* 证书和证书链详情完整显示
* FIPS 评估使用规则引擎
* 报告明确区分静态评估与正式认证
* Light、Dark、System 三种主题可用
* UI 长操作不阻塞
* 日志和报告不泄露秘密
* 转换结果经过重新加载验证
* 所有测试通过
* `./mvnw verify` 通过
* 文档完整

不要声称执行过未实际执行的命令，也不要声称应用通过正式 FIPS 认证。
