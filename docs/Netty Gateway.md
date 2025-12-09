# Beacon Provider Netty Gateway

本文档描述 Bukkit Beacon Provider Client 与 Forge/Fabric Mod 之间新的 Netty/TCP 通讯规范。设计目标：

1. 摆脱插件消息对在线玩家的依赖，服务器 0 人时也能访问 Provider；
2. 保持 Beacon `action` 请求/响应体 (见 `docs/Channel API.md`) 不变；
3. 提供统一的配置文件与握手鉴权机制，便于 Bukkit 和 Forge 共享端口/密钥；
4. 允许未来扩展心跳、服务端推送等能力。

## 1. 拓扑与生命周期

- Forge/Fabric Provider 模组在服务器启动阶段创建一个 Netty TCP Server（默认绑定 `127.0.0.1:28545`）。
- Bukkit 插件在 `onEnable` 时读取同一份配置文件，主动连接 Netty Server，完成握手后保持常驻连接。
- 连接断开后 Bukkit 自动重连（退避 1s/2s/5s…）；Provider 端在 `serverStopped` 时关闭监听。
- 同一端口可接受多个客户端连接（例如调试工具 + 正式插件）。`connectionId` 用于区分。

## 2. 配置文件

Provider 会在 `config/hydroline/beacon-provider.json` 自动生成配置（Fabric & Forge 均使用 `FabricLoader.getConfigDir()`/`FMLPaths.CONFIGDIR`）。结构：

```json
{
  "listenAddress": "127.0.0.1",
  "listenPort": 28545,
  "authToken": "change-me",
  "handshakeTimeoutSeconds": 10,
  "idleTimeoutSeconds": 240
}
```

- **listenAddress/Port**：Mod 端监听地址。Bukkit 端直接使用同配置发起连接。
- **authToken**：联调密钥。首次创建时写入 `change-me`，管理员需修改后同时更新 Bukkit 端配置；若为空则拒绝外部连接。
- **handshakeTimeoutSeconds**：客户端必须在该时间内完成握手，否则强制断连。
- **idleTimeoutSeconds**：连接在无收发且未发送 Ping 的情况下能保持的最长时间。

Bukkit 插件（运行在同一个 Mohist 根目录）也会读取此文件：

```java
Path configDir = Paths.get("config", "hydroline", "beacon-provider.json");
NettyConfig cfg = NettyConfig.load(configDir);
NettyClient.connect(cfg.listenAddress(), cfg.listenPort(), cfg.authToken());
```

> 如果后续需要把 Bukkit 插件放到独立代理/Velocity，可通过 rsync/NFS 将该 JSON 分发到对应机器，或改为自定义 HTTP 拉取。

## 3. 帧格式

- 物理层使用 TCP；逻辑层采用 Netty `LengthFieldBasedFrameDecoder + LengthFieldPrepender`。
- 每个帧 = `[length(4 bytes, big endian)] + [payload (UTF-8 JSON)]`。
- JSON 外层使用统一 Envelope：

```json
{
  "type": "handshake | handshake_ack | request | response | ping | pong | error",
  "timestamp": 1733836800000,
  "connectionId": "optional-uuid",
  "body": { ... depends on type ... }
}
```

### 3.1 握手阶段

1. **客户端 -> 服务端 (`handshake`)**

```json
{
  "type": "handshake",
  "body": {
    "protocolVersion": 1,
    "clientId": "bukkit-main",
    "token": "<config authToken>",
    "capabilities": ["actions", "events"]
  }
}
```

2. **服务端 -> 客户端 (`handshake_ack`)**

```json
{
  "type": "handshake_ack",
  "body": {
    "protocolVersion": 1,
    "connectionId": "6cb6098d-6f3f-4ed2-bb9f-2a0f7b90f57c",
    "serverName": "Hydroline Beacon Provider",
    "modVersion": "0.1.5",
    "heartbeatIntervalSeconds": 30,
    "message": "ready"
  }
}
```

若 `token` 不匹配或 `protocolVersion` 不兼容，服务端返回：

```json
{
  "type": "error",
  "body": {
    "errorCode": "AUTH_FAILED",
    "message": "Invalid token"
  }
}
```

并立即断开连接。

### 3.2 正常请求/响应

握手完成后即可复用现有 Beacon action 协议：

- **Request 帧**

```json
{
  "type": "request",
  "connectionId": "6cb6...",
  "body": {
    "protocolVersion": 1,
    "requestId": "hx8k0q1z9b2c",
    "action": "mtr:list_stations",
    "payload": { "dimension": "minecraft:overworld" }
  }
}
```

- **Response 帧**

```json
{
  "type": "response",
  "connectionId": "6cb6...",
  "body": {
    "protocolVersion": 1,
    "requestId": "hx8k0q1z9b2c",
    "result": "OK",
    "message": "",
    "payload": { "stations": [ ... ] }
  }
}
```

### 3.3 心跳 / 超时

- 任意一方可按 `heartbeatIntervalSeconds` 发送：

```json
{ "type": "ping", "body": { "seq": 42 } }
```

服务端必须在同一连接上回 `pong`。

- 若在 `idleTimeoutSeconds` 内既未收到应用层流量也未收到 Ping，将主动关闭连接。

## 4. 错误码

`error` 帧或 `response.body.result != OK` 时统一遵循：

| Code                   | 说明                                      |
| ---------------------- | ----------------------------------------- |
| `AUTH_FAILED`          | Token 缺失/不匹配                         |
| `HANDSHAKE_TIMEOUT`    | 在 `handshakeTimeoutSeconds` 内未完成握手 |
| `UNSUPPORTED_VERSION`  | `protocolVersion` 不受支持                |
| `REQUEST_DECODE_ERROR` | JSON 解析失败                             |
| `INTERNAL_ERROR`       | Provider 内部异常                         |

## 5. 实现检查清单

1. **公共模块**

   - `NettyConfig`：负责读取/写入 `config/hydroline/beacon-provider.json`，支持自动创建默认值；
   - `GatewayEnvelope` + `GatewayCodec`：统一序列化/反序列化；
   - `GatewayServer`：包装 Netty boss/worker 线程、连接注册、握手状态；
   - `GatewayClient`（Bukkit 复用，后续在插件仓库实现）。

2. **Forge/Fabric Loader**

   - 在 `ServerStarting` 时初始化 `GatewayServer`；`ServerStopped` 时关闭；
   - 将 `GatewayServer` 与 `ChannelMessageRouter` 打通，收到 request -> 调用 `router.handleIncoming(SymbolicConnectionId, payloadBytes)`；
   - 将 response 通过连接写回；
   - 维持 `connectionId -> ConnectionContext` 映射，供日志/鉴权使用。

3. **Bukkit 插件**
   - 迁移通信栈：改为读取 JSON config，建立 Netty client，按照本协议发送 `request/response`。
   - 继续复用 `docs/Channel API.md` 中 action 说明。

## 6. 兼容性

- 老的 Plugin Messaging 模式与新 Netty 模式互斥。Provider 1.0.0 起默认只启用 Netty；如需暂时兼容，可设置 `listenPort = 0` 表示只保留旧通道。
- `docs/Channel API.md` 仍描述逻辑层 payload，不涉及物理传输；Bukkit 适配后不再需要注册 `hydroline:beacon_provider` Channel。

## 7. FAQ

- **Q：同一 Mohist 实例中已经占用 8888 端口怎么办？**
  A：修改 `config/hydroline/beacon-provider.json` 的 `listenPort`，重启服务器即可。Bukkit 插件读取同文件后会自动使用新端口。

- **Q：如果未来想让 Velocity 连接 Provider？**
  A：保持该 TCP 协议不变即可，Velocity 端实现 `GatewayClient` 并提供 token 即可，不受 Minecraft 进程限制。

- **Q：需要 TLS/加密吗？**
  A：当前默认运行在同一物理机上的回环连接，未引入 TLS。若未来部署跨机，可在 Netty pipeline 中添加 SSL handler 并在配置中额外指定证书路径。
