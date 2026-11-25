# Computera · 计算时代

面向 Minecraft 的计算机科技模组，通过可编程终端组织设备、物流和自动化。

## 版本与安装

| 项目 | 版本 |
| --- | --- |
| Computera | 1.0.1 |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.128 |
| Java | 25 |
| 可选兼容 | IC2: The New Era 1.0.4 |

在仓库的 **Releases** 页面下载 `computera-1.0.1.jar`，放入对应 NeoForge 实例的 `mods` 目录；多人游戏需在服务端与客户端都安装。`computera-1.0.1-package.zip` 包含同一 JAR 与中文安装、使用说明，不要把 ZIP 本身当作模组安装。IC2 并非必需，也不包含在下载包中。升级前请备份存档。

## 主要功能

- 计算机机柜、五级计算卡、硬盘、闪存和打孔卡。
- 电传打字机与 CRT 终端，自研 Python 风格脚本子集。
- 数据线缆集群、无线容器绑定、设备发现和命名。
- 物流存取、分配、发放、管道搬运与自动化规则。
- 可合并的多方块集装箱，以及可拼接的网络显示屏。
- 从终端物流页打开设备原生界面。
- 可选 IC2 EU → FE 适配器，可经数据线缆给机柜供电。

入门：放置机柜并装入计算卡，用数据线缆连接终端和设备，向机柜供应 FE，然后在终端中编写或运行程序。电传终端需要纸带，CRT 活跃时消耗 3 FE/t。

完整玩法、终端命令和兼容边界见 [中文使用说明](docs/README_zh.md)，实现与验收入口见 [当前实现规格](docs/DESIGN.md)。

## 1.0.1 更新重点

- 修复终端远程打开设备原生界面后，因设备自身距离检查而立即关闭的问题。
- 远程界面使用终端或显示屏作为距离锚点；要求同维度、距锚点不超过 8 格，目标仍加载且接入同一集群。
- 包含此前物流资源守恒、会话校验、集装箱库存与分页，以及 EU 适配器数据线缆供电修复。

## 验证状态与限制

- 标准构建成功，脚本自测 63 项通过。
- 隔离服务端已通过集群、物流、集装箱与远程菜单回归；箱子、熔炉、IC2 1.0.4 发电机的打开调用链已验证。
- IC2 发电机经铜线、EU 适配器、两段数据线缆向机柜供电已实测，断线后停止供电。
- 服务端菜单测试使用 FakePlayer，不等同于真实客户端视觉与多人网络验收；1.0.1 的远程界面视觉及双真实玩家联机仍待人工验证。
- 使用专有打开协议或额外距离检查的第三方设备不保证兼容。
- 脚本不是完整 Python，不支持 `import` 或第三方 Python 库；旧的无资源来源 `put/take` 已禁用。
- 当前不包含无人机系统。

## 从源码构建

需要 JDK 25，使用随仓库提供的 Gradle Wrapper：

```powershell
.\gradlew.bat build
```

Linux / macOS：

```bash
bash gradlew build
```

产物为 `build/libs/computera-1.0.1.jar`；`build` 同时运行 `scriptSelfTest`。

当前 `gradle.properties` 保留开发环境的 `org.gradle.java.installations.paths` 本机路径。其他机器构建前，请将其改为自己的 JDK 25 路径，或删除该行并设置 `JAVA_HOME` 指向 JDK 25。自动下载 JDK 当前关闭。Gradle 与 NeoForge 仓库配置使用镜像；镜像不可达时可分别在 `gradle/wrapper/gradle-wrapper.properties`、`settings.gradle` 中改回官方源。

开发启动使用 `runClient` / `runServer`；隔离验收使用 `runAuditClient` / `runAuditServer`。服务端需自行阅读并接受 Minecraft EULA。管理员命令 `/computera selftest` 会临时放置测试方块，请仅在独立测试世界使用；电力验收 `/computera selftestpower` 另需自行安装兼容版本 IC2。

`tools/` 中的 Python 工具用于生成与预览部分素材，纹理生成和预览需要 Pillow；普通 Java 构建不需要这些工具。

## 权利与反馈

**All Rights Reserved（保留所有权利）。** 源码公开供查看，不代表采用开源许可证；未授予额外的使用、修改或再分发许可，第三方组件遵循其各自许可。

反馈问题时请提供 Minecraft、NeoForge、Computera 与相关模组版本，复现步骤及脱敏日志。请勿上传令牌、个人信息或私人服务器地址。
