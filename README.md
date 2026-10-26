# 区块链托管交易应用系统

2026《区块链技术与应用》大作业，第 10 组，必做选择**路线 A：Spring Boot + Web3j 平台后端**。

本项目在实验三的 Escrow 托管合约上实现 REST 接口。买卖家通过 Remix 和 MetaMask 创建、付款、确认及发起争议；后端账户担任仲裁人，通过 Web3j 签署仲裁交易。订单列表从链上的 `OrderCreated` 事件日志重建。

> 当前仓库处于初始化阶段，后端代码尚未加入。以下记录目标接口、接入步骤及验收要求；运行命令需在代码合入后与实际构建配置核对。标为“待填写”的内容必须用本次实际运行数据补齐，不能作为已完成证明。

## 小组分工

| 成员 | 姓名 | 负责内容 | 负责文件或材料 |
|---|---|---|---|
| 成员 1 | 待填写 | 合约封装、环境变量配置、Service，部署、加载、订单详情及仲裁 | `Escrow.sol`、代币源码、生成的 Java 封装、`EscrowService.java`、Web3j 配置，实际路径待代码合入后补齐 |
| 成员 2 | 待填写 | Controller、事件查询、接口联调 | `EscrowController.java`、Service 中的事件分批查询方法，实际路径待补齐 |
| 成员 3 | 饶心翊 | Remix 买卖家操作、验收记录、截图及报告汇总 | `README.md`、交易记录表、大作业报告 |

成员 1 和成员 2 共同维护 Service 时，先约定方法签名并协调修改。各成员提供自己负责部分的截图、解释和问题记录，组长汇总报告。

## 环境与前置条件

- Java、Gradle：具体版本待后端代码合入后，依据构建文件补充；优先使用仓库提供的 Gradle Wrapper。
- Spring Boot、Web3j：版本以实际构建文件为准。
- MetaMask：连接 POTOS 测试网，买卖家账户均有足够测试币支付 gas。
- Remix：编译与后端一致的 Escrow 和代币源码，用于买卖家操作。
- 买家持有至少 60 个本次使用的代币，用于两笔各 30 个代币的订单。
- 后端使用专门的测试网仲裁账户，持有足够测试币支付部署及仲裁费用。

| 网络参数 | 值 |
|---|---|
| 网络 | POTOS Testnet |
| RPC | `https://rpc-testnet.potos.hk`（按任务书，运行时核对可用性） |
| Chain ID | 待填写，以课程网络配置和钱包显示为准 |
| 区块浏览器入口 | 待填写，以课程提供的浏览器为准 |

## 配置与启动

以下以 Spring Boot 项目位于仓库根目录、采用任务书中的 Gradle 配置为例。若后端放在子目录，先进入实际后端目录再执行；合入代码后须更新本节，使其可以直接复现。

### 1. 配置环境变量

`src/main/resources/application.properties` 应通过占位符读取私钥，不得填写明文私钥：

```properties
web3j.client-address=https://rpc-testnet.potos.hk
web3j.private-key=${WEB3J_PRIVATE_KEY}
web3j.gas-price=1000000
web3j.gas-limit=6721975
```

gas 参数沿用任务书建议，实际部署前由后端负责人核对网络要求及余额。

Windows PowerShell 可用隐藏输入方式在当前终端设置变量：

```powershell
$keyInput = Read-Host '输入专用测试网仲裁账户私钥' -AsSecureString
$env:WEB3J_PRIVATE_KEY = [System.Net.NetworkCredential]::new('', $keyInput).Password
Remove-Variable keyInput
```

在同一个终端启动服务。IDE 启动时，在 Run Configuration 的 Environment variables 中设置 `WEB3J_PRIVATE_KEY`。不得把私钥写入 README、源码、配置、截图、提交记录或共享的 IDE 配置；不要向他人发送私钥或助记词。

### 2. 生成 Java 合约封装

将本组实际使用的 Escrow 与代币源码放入 `src/main/resources/contracts/`。代币名称以实际源码为准，不应直接假定为示例的 `ZSToken`。

Windows PowerShell：

```powershell
.\gradlew.bat generateContractWrappers
```

Linux / macOS：

```bash
bash gradlew generateContractWrappers
```

生成位置以构建配置和输出日志为准。检查封装中的 Order 结构、事件定义及事件解码方法，并保留生成成功截图。

### 3. 启动后端

Windows PowerShell：

```powershell
.\gradlew.bat bootRun
```

Linux / macOS：

```bash
bash gradlew bootRun
```

任务书默认接口地址为 `http://localhost:8080`。实际端口、目录及版本在代码合入并验证后补充。

## 必做接口

以下是任务书要求，当前尚待代码实现与验收。

| 方法 | 路径 | 参数 | 主要返回内容 |
|---|---|---|---|
| POST | `/api/escrow/deploy` | 无 | `contractAddress`、`transactionHash`、`deployBlock` |
| POST | `/api/escrow/load` | `address`、`fromBlock` | `contractAddress` |
| GET | `/api/escrow/address` | 无 | `contractAddress`、`arbiter` |
| GET | `/api/escrow/orders/{id}` | 路径中的订单 ID | `buyer`、`seller`、`token`、`amount`、`deadline`、文字 `status`、`description` |
| GET | `/api/escrow/orders` | 无 | 从 `OrderCreated` 重建的订单列表，含 `id`、`buyer`、`seller`、`amount`、`blockNumber`、`transactionHash` |
| POST | `/api/escrow/orders/{id}/resolve` | `sellerWins=true/false` | `transactionHash`、`blockNumber`、`status` |

订单列表不得以循环读取 `getOrder(0)` 到 `getOrder(orderCount-1)` 代替事件查询。按任务书中的 POTOS RPC 限制，每次 `eth_getLogs` 只查询最多 10 个区块，从部署区块起分批查询并合并；事件列表不代表订单最新状态，详情通过 `getOrder` 查询。

加载已有合约时，`fromBlock` 填实际部署区块。若服务未持久化合约信息，重启后需重新 load；最终行为以实现为准并在此更新。

## 合约与账户信息

所有地址必须来自本组本次实际部署和验收。路线 A 由后端重新部署 Escrow，并将后端签名账户设为仲裁人。实验三旧 Escrow 地址及旧交易不能代替本次记录。

| 项目 | 实际值 |
|---|---|
| 本仓库地址 | 待填写 |
| 后端签名账户 / 仲裁人 | 待填写 |
| 本次新 Escrow 地址 | 待填写 |
| Escrow 部署区块 | 待填写 |
| 代币合约地址 | 待填写 |
| 代币名称 / 精度 | 待填写 |
| 买家 A 姓名及地址 | 待填写 |
| 卖家 B 姓名及地址 | 待填写 |

## Remix 操作与完整验收

### 操作准备

1. 在 Remix 编译本次实际使用的 Escrow 和代币源码，连接 MetaMask，确认是 POTOS 测试网。
2. 后端调用 deploy，记录新 Escrow 地址、部署区块和交易哈希，再查询 address 核对仲裁人。
3. Remix 选择 Escrow，通过 **At Address** 加载后端部署的新地址；同样加载代币合约。
4. 检查 `arbiter()`、`orderCount()`、代币的 `decimals()` 与 `balanceOf(买家)`。全新 Escrow 的订单数应为 0，否则先核对实际订单编号。
5. 记录买家、卖家和 Escrow 的初始代币余额。买卖家各自用自己的钱包签署交易。

若代币精度为 18，30 个代币对应 `30000000000000000000`，60 个对应 `60000000000000000000`。Remix 的 amount 输入为最小单位，不会自动换算。以下函数调用的原生币 VALUE 保持 0。

创建时 deadline 为当前时间之后 30 分钟的 Unix 秒数。可在浏览器开发者工具控制台计算，每次创建前检查仍在未来：

```javascript
Math.floor(Date.now() / 1000) + 30 * 60
```

### 验收顺序

以下订单编号以新合约从 0 开始为前提。每笔写交易都等待确认后再执行下一步。

| 步骤 | 操作人及位置 | 操作 | 预期结果 |
|---|---|---|---|
| 1 | 后端 | 部署 Escrow，查询地址和 arbiter | 仲裁人为后端签名账户 |
| 2 | 买家 A，Remix | 创建两笔订单，卖家 B、选定代币、每笔30个、30分钟后到期，分别填写描述 | 生成订单0、1，均为 Created |
| 3 | 买家 A，Remix | 在代币合约 approve(新Escrow地址, 60个的最小单位)，确认后分别 deposit(0)、deposit(1) | 两笔订单均为 Paid；授权对象是新Escrow |
| 4 | 卖家 B，Remix | dispute(1) | 订单1为 Disputed |
| 5 | 后端 | 查询 orders 列表和订单0、1详情 | 列表有两笔，订单0 Paid、订单1 Disputed |
| 6 | 后端 | resolve(0, true) | 因订单0未争议而失败，保留错误及实际交易证据 |
| 7 | 后端 | resolve(1, false)，再查订单1 | 成功退款，订单1为 Refunded |
| 8 | 买家 A，Remix | confirm(0)，查询订单与代币余额 | 订单0为 Completed，卖家收到30个代币 |

后端调用示例（Windows PowerShell 使用 `curl.exe`，Linux / macOS 使用 `curl`）：

```powershell
curl.exe -X POST http://localhost:8080/api/escrow/deploy
curl.exe http://localhost:8080/api/escrow/address
curl.exe http://localhost:8080/api/escrow/orders
curl.exe http://localhost:8080/api/escrow/orders/0
curl.exe http://localhost:8080/api/escrow/orders/1
curl.exe -X POST "http://localhost:8080/api/escrow/orders/0/resolve?sellerWins=true"
curl.exe -X POST "http://localhost:8080/api/escrow/orders/1/resolve?sellerWins=false"
curl.exe http://localhost:8080/api/escrow/orders/1
```

这些命令应按上表顺序与 Remix 操作交替执行，不能在刚部署后一次性执行全部命令。不要在步骤6之前确认订单0。

仅有这两笔订单且初始无托管余额时，验收结束后 Escrow 代币余额应为0；买家相对初始净减少30个，卖家净增加30个。最终填入实际查询值和截图。

## 交易记录表

下面前8行对应任务书要求的8项记录，额外两行补充另一笔创建和付款。README 与报告保持一致，所有已广播交易填写可访问的真实区块浏览器链接。

| 操作 | 交易哈希 | 区块浏览器链接 | 发起账户 |
|---|---|---|---|
| 部署 Escrow（本次后端部署） | 待填写 | 待填写 | 待填写 |
| createOrder 订单0 | 待填写 | 待填写 | 待填写 |
| approve | 待填写 | 待填写 | 待填写 |
| deposit 订单0 | 待填写 | 待填写 | 待填写 |
| dispute 订单1 | 待填写 | 待填写 | 待填写 |
| resolve 订单1 | 待填写 | 待填写 | 待填写 |
| confirm 订单0 | 待填写 | 待填写 | 待填写 |
| resolve 失败（订单0） | 待填写 | 待填写 | 待填写 |
| createOrder 订单1 | 待填写 | 待填写 | 待填写 |
| deposit 订单1 | 待填写 | 待填写 | 待填写 |

失败操作要区分“发送前估算失败”和“已上链回滚”。未广播时没有链上交易哈希，应如实记录并补充错误截图；为满足任务书对失败交易记录的验收要求，由后端负责人核对并保留实际上链失败交易证据。不得编造哈希。已上链回滚仍消耗 gas，单纯估算失败不能写成已支付链上 gas。

## 报告截图与提交材料

| 编号 | 必须保存的内容 |
|---|---|
| 4A-1 | 使用环境变量占位符的 application.properties，不含明文私钥 |
| 4A-2 | Java 封装生成日志、生成文件列表、Order 与事件解码代码 |
| 4A-3 | SimpleStorage 部署、设置、读取三个接口响应及 set 交易浏览器详情 |
| 4A-4 | EscrowService 与 EscrowController 完整代码，可分多张 |
| 4A-5 | 部署响应和 Remix 中创建、授权、付款、争议的关键操作 |
| 4A-6 | 两笔订单列表及详情，展示 Paid 和 Disputed |
| 4A-7 | 仲裁订单0失败、仲裁订单1成功的响应 |
| 4A-8 | 成功 resolve 的浏览器详情，包含 From 和 Logs |

报告按统一模板填写，保留路线A、删除路线B，回答解释题4A-1至4A-6。每道解释引用本组截图的具体数值，并说明材料所属成员与操作角色。截图必须真实，不得拼接或修改交易结果。

提交材料：可访问的 Git 仓库、完整 README、按模板填写的大作业报告；仓库地址写入报告首页及提交邮件正文。报告文件名按 `班级_组号_组长姓名_组长学号_大作业报告.docx` 填写，邮件标题与文件名一致。具体提交时间以课程通知为准。

评分标准中加分项累计最多20分、总成绩封顶100分；本项目当前未声明完成任何加分项。

## 提交前检查

- 后端代码已合入，补齐实际依赖版本、目录和可复现的运行说明。
- 六个接口完成，订单列表确实来自事件日志；按最多10个区块分批查询。
- 所有关键地址、交易哈希、链接和发起账户已填写并核对，README与报告一致。
- 8组截图、6道解释题齐全，组员能解释自己的代码、截图和交易数据。
- 仓库对助教可读，报告包含分工表及真实贡献比例。
- 检查所有文件和历史提交中有无私钥，包含示例自带私钥；环境变量实际值、`.env` 等秘密文件不得提交。若曾泄露，先更换账户并处理泄露记录。
- 报告末尾填写AI使用说明：工具、使用步骤、错误内容及发现和修正过程。当前已使用 OpenAI Codex 辅助分析任务书及整理本 README；后续使用情况和实际修正记录继续补充。
