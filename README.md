<h1><img src="icon.png" width="56" height="56" align="absmiddle" alt=""> Computera 2</h1>

**简体中文** · [English](README.en.md)

**Computera 2（计算时代2：新纪元）** 是 [IndustrialCraft²: The New Era（工业时代2：新纪元）](https://github.com/spectrum527/IndustrialCraft2-The-New-Era) 的计算机与物流自动化附属模组，也可独立安装运行。

## 下载与安装

**当前正式版为 1.0.1。** 从 Releases 下载 `computera-1.0.1.jar`，放入对应 NeoForge 实例的 `mods/` 目录。联机时客户端与专服需安装同一份 JAR。发布页的 `computera-1.0.1-package.zip` 内含同一份模组 JAR 与中文说明文档，请勿将 ZIP 压缩包直接放入 `mods/`。升级版本前请备份存档。 [发行页面](https://github.com/MongolslingoNavy/computera-minecraft-mod/releases/tag/v1.0.1) · [中文使用说明](docs/README_zh.md) · [当前实现规格](docs/DESIGN.md)

| Minecraft | 加载器 | Java | 可选联动 | 下载 |
| --- | --- | ---: | --- | --- |
| 26.1.2 | NeoForge 26.1.2.128 | 25 | IC2: The New Era 1.0.4+ | [NeoForge 26.1.2 JAR](https://github.com/MongolslingoNavy/computera-minecraft-mod/releases/tag/v1.0.1) |

算力集群从计算机机柜开始搭建。单台机柜提供 8 个计算卡槽与 2 个硬盘槽，装入 I 至 V 级计算卡可分别提供 2/4/8/16/32 线程；同维度内的设备通过面贴或数据线缆相连，即可组成同一集群，线缆搜索距离不超过 64 格，单集群最多容纳 32 台机柜（上限 8192 线程）。电传打字机终端采用纯文本命令行，需装入纸带（8192 行/卷）并按输出行数消耗；CRT 显示终端在有人查看或运行程序时从主机柜扣除 3 FE/t，提供图形调度界面（支持多人同时打开，共用同一线程与输出）并可切回命令行。终端内置 Python 风格脚本子集，代码经词法与语法分析编译为字节码，每集群每 tick 共有 200,000 指令预算，由运行中的终端均分执行。程序保存在机柜或硬盘（每块 16 个文件，通过 `hd1:` 与 `hd2:` 访问）中，可用闪存模块或贴靠终端的打孔机配合打孔卡迁移。游戏内合成《计算时代手册》可随时查阅 11 页入门指南。 [玩家指南与终端命令](docs/README_zh.md)

接入集群的方块若注册了 NeoForge 标准物品或流体能力，会自动归入 `devices` 命名空间供脚本与界面调用；使用网络连接器先右键机柜设为主节点，再右键远端容器，即可免线缆完成无线绑定。容器贴上物流端口后可手持物品设定过滤并建立全网索引，集群内接入物流管道即可按每条管道每 tick 4 个物品的吞吐量，把无过滤源端口中的物品自动搬运至过滤匹配的目标端口。CRT 物流页支持整组或单件拿放、容器改名与分组，可向多个容器平均分配或按每箱指定数量批量发放物品，并能配置带图标选择器的定时搬运规则。从物流页点击「原界面」可远程打开目标方块原生 GUI；1.0.1 版以所用的终端或显示屏为 8 格距离锚点，只要目标仍在同一维度加载且保持集群连接即可正常交互。面贴放置的集装箱最多可将 64 个单箱（每个 27 格，合计 1728 格）合并为同一翻页容器（每页 54 格），拆除单个箱体仅掉落该箱自身的物品并自动重分组。贴在方块侧面的网络显示屏在同朝向相邻时自动拼接为大屏，每 3 秒（60 tick）刷新一次集群总览、物品清单或容器运行状态；右键屏幕可就近唤起集群终端界面，潜行右键打开屏幕状态页。 [实现规格与网络会话](docs/DESIGN.md)

安装 [IndustrialCraft²: The New Era](https://github.com/spectrum527/IndustrialCraft2-The-New-Era)（26.1.2 分支）后，其打粉机、电炉与压缩机等机器均已注册标准物品和流体能力，可直接接入数据线缆或物流端口，在显示屏上查看加工状态，或由脚本与物流系统读写槽位。IC2 的 EU 电网独立于 NeoForge 标准能量接口，EU 适配器通过运行时反射直接接入该电网（无需将 IC2 作为编译依赖），按 `1 EU = 4 FE`（单包上限 32 EU，内部缓冲 100,000 FE）将电能转换为 FE。每个 EU 适配器最高输出 1000 FE/t，既可紧贴机柜直接供电，也可紧贴数据线缆向同集群机柜输电；未安装 IC2 时该方块仍可正常放置，界面显示 `not installed`。 [兼容说明与电力自测](docs/README_zh.md#eu-适配器)

## 实现边界、美术与授权

本模组的脚本运行在自研字节码虚拟机上，支持缩进块、条件分支与循环，可定义函数并使用列表和字典，内置 `sleep`、`time` 与 `log` 等函数，不支持 `import`、类定义、`try/except` 或第三方 Python 库。为保证物品与流体守恒，容器设备仅开放 `size/get/id/count` 与同容器内 `move(from, to, n)` 接口，流体设备仅开放只读查询；旧版无来源的 `put` 与无去向的 `take` 已禁用并会在运行时显式报错。每个接入集群的终端固定占用 1 线程，运行程序期间额外占用 1 线程。机柜自身不发电，目前也不收取机柜待机功耗或按线程计费；CRT 终端断电会立即中断正在运行的程序且不自动续跑。当前版本不包含无人机系统与自定义音效。 [脚本与使用边界](docs/README_zh.md#脚本与使用边界)

项目标准构建包含 63 项 `scriptSelfTest` 字节码与语法回归测试。隔离服务端已通过集群组网与物流守恒自测，覆盖集装箱合并翻页、双 FakePlayer 并发会话，以及原版箱子、熔炉与 IC2 1.0.4 发电机的远程原生菜单打开调用链。电力联动已在安装 IC2 1.0.4 的环境下实测，燃煤发电机经铜线、EU 适配器与两段数据线缆向机柜供电 200 tick 共充入 7920 FE，拆断线缆后供电立即停止。服务端 FakePlayer 测试不等同于真实客户端渲染与双玩家联机验收；第三方模组方块若采用自定义打开协议或额外距离校验，则不保证支持远程打开原生界面。模组内全部方块贴图、物品图标与线缆模型均由 `tools/` 目录下的 Python 脚本程序化生成，未使用第三方美术素材。 [验证入口与实测记录](docs/DESIGN.md#验证入口)

**All Rights Reserved（保留所有权利）。** 仓库公开源码仅供查阅，未采用开源许可证，也未授予额外的使用、修改或再分发许可。项目依赖的第三方组件遵循其各自许可证。

## 从源码构建与反馈

构建需准备 JDK 25，并使用仓库自带的 Gradle Wrapper。当前 `gradle.properties` 中的 `org.gradle.java.installations.paths` 指向开发环境本机路径，且已关闭自动下载 JDK；在其他机器构建前，请将该配置改为本机 JDK 25 路径，或删除该行并将 `JAVA_HOME` 指向 JDK 25。`gradle/wrapper/gradle-wrapper.properties` 与 `settings.gradle` 默认配置了国内镜像源，镜像不可达时可改回官方源。`tools/` 中的纹理与模型生成预览脚本依赖 Python 与 Pillow，日常 Java 编译打包无需运行。

开发调试可执行 `runClient` 或 `runServer`，隔离环境验收可使用 `runAuditClient` 与 `runAuditServer`（运行服务端需自行阅读并同意 Minecraft EULA）。游戏内管理员命令 `/computera selftest` 会在玩家附近临时生成测试方块并在验收后清理，请仅在独立测试存档中执行；安装兼容版本 IC2 后还可执行 `/computera selftestpower` 验证 EU 转 FE 跨线缆供电。反馈问题时请附上 Minecraft 与 NeoForge 版本、Computera 2 及相关模组版本号，并提供复现步骤与脱敏日志，请勿上传登录令牌、个人信息或私有服务器地址。 [提交问题](https://github.com/MongolslingoNavy/computera-minecraft-mod/issues)

```sh
# Linux / macOS，编译打包并运行 63 项脚本自测
bash gradlew build

# Windows PowerShell，产物位于 build/libs/computera-1.0.1.jar
.\gradlew.bat build
```
