# 区块链托管交易应用系统

2026《区块链技术与应用》大作业，第 10 组，必做选择**路线 A：Spring Boot + Web3j 平台后端**。

本项目在实验三的 Escrow 托管合约上实现 REST 接口。买卖家通过 Remix 和 MetaMask 创建、付款、确认及发起争议；后端账户担任仲裁人，通过 Web3j 签署仲裁交易。订单列表从链上的 `OrderCreated` 事件日志重建。

本仓库已包含后端、Solidity 源码和生成的 Java 合约封装。下文的地址与交易记录来自本组 2026 年 10 月 27 日在 POTOS Testnet 的验收；提交前应逐条核对原始交易回执与报告记录。

## 小组分工

| 成员 | 姓名 | 负责内容 | 负责文件或材料 |
|---|---|---|---|
| 成员 1 | 杨铱玫 | 合约封装、环境变量配置、Service、部署及仲裁 | `src/main/resources/contracts/`、`src/main/java/com/wetech/demo/web3j/contracts/`、`config/Web3jConfig.java`、`service/EscrowService.java` |
| 成员 2 | 戴柔柔 | Controller、事件查询、接口联调及卖家争议操作 | `controller/EscrowController.java`、`service/EscrowService.java` 中的事件查询方法 |
| 成员 3 | 饶心翊 | Remix 买卖家操作、验收记录、截图及报告汇总 | `README.md`、交易记录表、大作业报告 |

上表为目前的实际分工；提交报告前按最终提交记录核对每人的负责文件和贡献比例。

## 环境与前置条件

- JDK 21：`build.gradle` 将 `sourceCompatibility` 和 `targetCompatibility` 均设为 21。
- Gradle 8.13：由仓库中的 Gradle Wrapper 指定，无需另行安装 Gradle。
- Spring Boot 3.2.3、Web3j 4.14.0：由 `build.gradle` 指定。
- MetaMask：连接 POTOS 测试网，买卖家账户均有足够测试币支付 gas。
- Remix：编译与后端一致的 Escrow 和代币源码，用于买卖家操作。
- 买家持有至少 60 个本次使用的代币，用于两笔各 30 个代币的订单。
- 后端使用专门的测试网仲裁账户，持有足够测试币支付部署及仲裁费用。

| 网络参数 | 值 |
|---|---|
| 网络 | POTOS Testnet |
| RPC | `https://rpc-testnet.potos.hk` |
| 区块浏览器入口 | https://scan-testnet.potos.hk/ |

## 配置与启动

Spring Boot 项目位于仓库根目录。以下命令在该目录执行，服务监听 `http://localhost:8080`。

### 1. 配置环境变量

`src/main/resources/application.properties` 已通过占位符读取私钥：

```properties
web3j.client-address=https://rpc-testnet.potos.hk
web3j.private-key=${WEB3J_PRIVATE_KEY}
web3j.gas-price=1000000
web3j.gas-limit=6721975
```

RPC、gas price 和 gas limit 与仓库中的当前配置一致。发送交易前检查测试币余额。

Windows PowerShell 可用隐藏输入方式在当前终端设置变量：

```powershell
$keyInput = Read-Host '输入专用测试网仲裁账户私钥' -AsSecureString
$env:WEB3J_PRIVATE_KEY = [System.Net.NetworkCredential]::new('', $keyInput).Password
Remove-Variable keyInput
```

在同一个终端启动服务。IDE 启动时，在 Run Configuration 的 Environment variables 中设置 `WEB3J_PRIVATE_KEY`。不得把私钥写入 README、源码、配置、截图、提交记录或共享的 IDE 配置；不要向他人发送私钥或助记词。

### 2. 生成 Java 合约封装

本组使用的 `Escrow.sol`、`RXYToken.sol` 与示例 `SimpleStorage.sol` 已在 `src/main/resources/contracts/`。生成的封装位于 `src/main/java/com/wetech/demo/web3j/contracts/`，含 `escrow/Escrow.java` 与 `rxytoken/RXYToken.java`。

Windows PowerShell：

```powershell
.\gradlew.bat generateContractWrappers
```

Linux / macOS：

```bash
bash gradlew generateContractWrappers
```

如修改 Solidity 源码，重新生成封装并检查 Order 结构和事件解码方法。生成操作需要可访问的依赖仓库。

### 3. 启动后端

Windows PowerShell：

```powershell
.\gradlew.bat bootRun
```

Linux / macOS：

```bash
bash gradlew bootRun
```

启动后，接口地址为 `http://localhost:8080`。重新启动后，服务内存中保存的合约地址会丢失，应先调用 `/api/escrow/load` 加载已部署的 Escrow，避免为恢复查询而再次部署。

## 必做接口

以下六个接口已在 `EscrowController.java` 中实现；返回字段按当前代码列出。

| 方法 | 路径 | 参数 | 主要返回内容 |
|---|---|---|---|
| POST | `/api/escrow/deploy` | 无 | `contractAddress`、`transactionHash`、`deployBlock` |
| POST | `/api/escrow/load` | `address`、`fromBlock` | `contractAddress` |
| GET | `/api/escrow/address` | 无 | `contractAddress`、`arbiter` |
| GET | `/api/escrow/orders/{id}` | 路径中的订单 ID | `buyer`、`seller`、`token`、`amount`、`deadline`、文字 `status`、`description` |
| GET | `/api/escrow/orders` | 无 | 从 `OrderCreated` 重建的订单列表，含 `id`、`buyer`、`seller`、`amount`、`blockNumber`、`transactionHash` |
| POST | `/api/escrow/orders/{id}/resolve` | `sellerWins=true/false` | `transactionHash`、`blockNumber`、`status` |

`EscrowService.listOrders()` 从部署区块起，按每次最多 10 个区块分批查询 `OrderCreated` 日志并合并结果；失败时最多尝试 3 次。列表返回创建事件中的数据，**不包含订单当前状态**；当前状态由 `/orders/{id}` 调用合约 `getOrder` 查询。

加载已有合约时，`fromBlock` 填实际部署区块，供事件查询从该区块开始。`/load` 只在当前服务进程内保存合约地址和起始区块。

## 合约与账户信息

路线 A 由后端重新部署 Escrow，并将后端签名账户设为仲裁人；代币沿用组长实验三部署的 RXYToken。

| 项目 | 实际值 |
|---|---|
| 本仓库地址 | https://github.com/r10381646-cmyk/blockchain-application-system |
| 后端签名账户 / 仲裁人 | `0x6f6E5f3390e4309B3454Bf750F2475f59bF7E2a8` |
| 本次新 Escrow 地址 | `0x42CA3D8233428DD7efbf3cE05a187F775aA0d4eF` |
| Escrow 部署区块 | `2693226` |
| 代币合约地址 | `0xfd7F21bA96ef87C881BA488bA3908DF16304E9D6` |
| 代币名称 / 精度 | `RXYToken`，18 位；符号 `RXY202430552631` |
| 买家 A 姓名及地址 | 饶心翊，`0xfBc444e948331FD4f06F1bd7ACad20eB2197F87A` |
| 卖家 B 姓名及地址 | 戴柔柔，`0x3ce0177aF1A0e2538A402C4b91d10ff94a4BC2D8` |

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

以下为本次验收使用的订单 0 和订单 1 的操作顺序。

| 步骤 | 操作人及位置 | 操作 | 预期结果 |
|---|---|---|---|
| 1 | 后端 | 部署 Escrow，查询地址和 arbiter | 仲裁人为后端签名账户 |
| 2 | 买家 A，Remix | 创建订单 0 和 1 | 两笔订单均产生 OrderCreated 事件 |
| 3 | 买家 A，Remix | 在代币合约 approve(新Escrow地址, 60个的最小单位)，确认后分别 deposit(0)、deposit(1) | 两笔订单均为 Paid；授权对象是新Escrow |
| 4 | 卖家 B，Remix | dispute(1) | 订单1为 Disputed |
| 5 | 后端 | 查询 orders 列表和订单0、1详情 | 列表包含订单0和1；当时订单0 Paid、订单1 Disputed |
| 6 | 后端 | resolve(0, true) | 订单0未争议，交易在区块2701717执行失败，使用25,884 gas |
| 7 | 后端 | resolve(1, false)，再查订单1 | 交易在区块2701725成功，订单1为 Refunded，30个代币退给买家 |
| 8 | 买家 A，Remix | confirm(0)，查询订单与代币余额 | 交易在区块2701786成功，订单0为 Completed，卖家余额为30个代币 |

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

若需在重启后继续查询本次已部署的 Escrow，使用以下命令恢复服务中的合约地址；把 `FROM_BLOCK` 换成部署交易的实际区块号：

```powershell
curl.exe -X POST "http://localhost:8080/api/escrow/load?address=0x42CA3D8233428DD7efbf3cE05a187F775aA0d4eF&fromBlock=2693226"
curl.exe http://localhost:8080/api/escrow/address
```

上述 `/deploy` 仅用于首次部署；要复查本次验收，使用 `/load`。其余命令按上表顺序与 Remix 操作交替执行，不能在刚部署后一次性执行全部命令。不要在步骤6之前确认订单0。

订单 0 完成后，`getOrder(0)` 返回状态 `Completed`；订单 1 经仲裁后为 `Refunded`。卖家 `balanceOf` 查询结果为30个代币。若报告要写“余额增加30个”，还须附卖家操作前余额的截图；仅凭最终余额不能证明增量。

## 交易记录表

以下哈希来自本组 Remix 记录、后端响应、区块浏览器截图和卖家提供的交易记录。提交前应逐行点击链接，核对交易方法、From、To 与状态。

| 操作 | 交易哈希 | 区块浏览器链接 | 发起账户 |
|---|---|---|---|
| 部署 Escrow（本次后端部署） | `0x9ff0e4af528faa78fc2f05ff2bd2716031cf77024093f3187841b79abf175b2b` | [区块浏览器](https://scan-testnet.potos.hk/tx/0x9ff0e4af528faa78fc2f05ff2bd2716031cf77024093f3187841b79abf175b2b) | 后端仲裁账户 |
| createOrder 订单0 | `0xb6c689e2f2ae9e107aa2046d7317d53e5db6a39fe79acf92a23b6ed769ce9cb4` | [区块浏览器](https://scan-testnet.potos.hk/tx/0xb6c689e2f2ae9e107aa2046d7317d53e5db6a39fe79acf92a23b6ed769ce9cb4) | 买家 A |
| createOrder 订单1 | `0xeec5469bfe0878894e79f04fa98bcbb2b2f34f6a4add314e700cc9406ba209a7` | [区块浏览器](https://scan-testnet.potos.hk/tx/0xeec5469bfe0878894e79f04fa98bcbb2b2f34f6a4add314e700cc9406ba209a7) | 买家 A |
| approve，授权新 Escrow 60 个代币 | `0x9ada00bfba6ecf9dc867b36bee1eecb1712bf875ef582631fdb35ad567810c0a` | [区块浏览器](https://scan-testnet.potos.hk/tx/0x9ada00bfba6ecf9dc867b36bee1eecb1712bf875ef582631fdb35ad567810c0a) | 买家 A |
| deposit 订单0 | `0xe8a315dad667c493c98d499c1efc0dce074ecac435f451dff7354cd4b5cfc16a` | [区块浏览器](https://scan-testnet.potos.hk/tx/0xe8a315dad667c493c98d499c1efc0dce074ecac435f451dff7354cd4b5cfc16a) | 买家 A |
| deposit 订单1 | `0x9d08b0f4ae95121768b99f14a0d8c58d7df104ee7087a673490ee6354c1a77be` | [区块浏览器](https://scan-testnet.potos.hk/tx/0x9d08b0f4ae95121768b99f14a0d8c58d7df104ee7087a673490ee6354c1a77be) | 买家 A |
| dispute 订单1 | `0x966b182e4283589ac79cbb8c340f78ad909e1822df20f891d2246c0bc3dd4f06` | [区块浏览器](https://scan-testnet.potos.hk/tx/0x966b182e4283589ac79cbb8c340f78ad909e1822df20f891d2246c0bc3dd4f06) | 卖家 B |
| resolve 失败（订单0） | `0x3b553d20affb129e88c7e32b4fd5bb695bf1c87259563e6b798b07095eb7d96c` | [区块浏览器](https://scan-testnet.potos.hk/tx/0x3b553d20affb129e88c7e32b4fd5bb695bf1c87259563e6b798b07095eb7d96c) | 后端仲裁账户 |
| resolve 订单1，退还买家 | `0x67bdb6b53c95f4a6565dd69ee714e2e9a67e7478a2610bd432fd1bc553dd4a52` | [区块浏览器](https://scan-testnet.potos.hk/tx/0x67bdb6b53c95f4a6565dd69ee714e2e9a67e7478a2610bd432fd1bc553dd4a52) | 后端仲裁账户 |
| confirm 订单0 | `0x67c998238a56f81d1b2ef33a992b48fa2285330650870ff66131d6ced4d2a4c8` | [区块浏览器](https://scan-testnet.potos.hk/tx/0x67c998238a56f81d1b2ef33a992b48fa2285330650870ff66131d6ced4d2a4c8) | 买家 A |

本次订单 0 的失败仲裁已上链，区块浏览器显示状态 Failed、区块 2701717、gasUsed 25,884、From 为后端仲裁账户。后端接口返回 HTTP 500；合约回滚后订单 0 仍为 Paid。区块浏览器截图仅显示 Base gas fee 为 0，不能仅据此推断整笔交易支付的 POT 数额。

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

