# Beacon Provider Channel API

本文件描述 Bukkit 端如何通过 Minecraft 自带的 Plugin Messaging Channel 与 Beacon Provider Mod 通信。所有字段均使用 UTF-8 JSON 编码，消息大小请保持在 2KB 以内，以便在不同 loader 间稳定转发。

## 1. Channel 约定

- **Channel 前缀**：`hydroline`
- **完整 Channel 名**：`hydroline:beacon_provider`
- **方向**：双向（Bukkit -> Mod -> Bukkit）。Bukkit 发送请求到该 channel，Mod 解析后通过同一 channel 回传。
- **并发**：无需额外连接，依赖请求内的 `requestId` 来区分多条并发消息（必须随每个请求携带）。
- **通信模型**：每条请求只映射一条响应；channel 不保存状态，也不具备广播/队列语义，等同于一次轻量 RPC。

Bukkit 需要在 `onEnable` 中注册：

```java
Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "hydroline:beacon_provider");
Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "hydroline:beacon_provider", listener);
```

## 2. 请求格式（Bukkit -> Mod）

```json
{
  "protocolVersion": 1,
  "requestId": "hx8k0q1z9b2c",
  "action": "beacon:invoke",
  "payload": { "...": "..." }
}
```

| 字段              | 类型     | 说明                                                                                        |
| ----------------- | -------- | ------------------------------------------------------------------------------------------- |
| `protocolVersion` | `int`    | 当前版本固定为 `1`。Mod 会拒绝非匹配版本。                                                  |
| `requestId`       | `string` | 必填，定长 12 位 `[0-9a-z]` 随机字符串；Mod 侧会拒绝缺失或格式不符的请求。                  |
| `action`          | `string` | 逻辑方法名称，例如 `beacon:ping`、`mtr:get_routes`。如果缺省，默认按 `beacon:invoke` 处理。 |
| `payload`         | `object` | Action 自定义参数。为空时可发送 `{}`。                                                      |

> 提示：建议先调用 `beacon:ping`，确认 channel 可用后再发送实际业务指令。

### RequestId 规范

- 长度固定 12 个字符，可根据需要在 Bukkit 端实现 `ThreadLocalRandom`/`SecureRandom` 生成。
- 字符集限制为 `0-9` 与 `a-z`，全部小写，便于快速校验。
- Mod 在反序列化阶段严格校验，缺失或格式错误会直接以 `INVALID_PAYLOAD` 拒绝。

示例（Bukkit 端伪代码）：

```java
private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
private static final int REQUEST_ID_LENGTH = 12;

private static String nextRequestId() {
  ThreadLocalRandom random = ThreadLocalRandom.current();
  char[] buffer = new char[REQUEST_ID_LENGTH];
  for (int i = 0; i < REQUEST_ID_LENGTH; i++) {
    buffer[i] = ALPHABET[random.nextInt(ALPHABET.length)];
  }
  return new String(buffer);
}
```

## 3. 响应格式（Mod -> Bukkit）

```json
{
  "protocolVersion": 1,
  "requestId": "hx8k0q1z9b2c",
  "result": "OK",
  "message": "optional description",
  "payload": { "data": "..." }
}
```

| 字段              | 类型     | 说明                                    |
| ----------------- | -------- | --------------------------------------- |
| `protocolVersion` | `int`    | 与请求一致。当前为 `1`。                |
| `requestId`       | `string` | 与请求对应，确保客户端能匹配 1:1 返回。 |
| `result`          | `string` | 见下表错误码。                          |
| `message`         | `string` | 可读文本，便于 Debug/提示。             |
| `payload`         | `object` | Action 自定义返回体，默认为 `{}`。      |

### ResultCode 列表

| Code              | 场景                                 |
| ----------------- | ------------------------------------ |
| `OK`              | 请求成功，`payload` 包含有效数据。   |
| `BUSY`            | Mod 端限流中，请稍后重试。           |
| `INVALID_ACTION`  | `action` 未注册或拼写错误。          |
| `INVALID_PAYLOAD` | JSON 解析失败或字段缺失。            |
| `NOT_READY`       | Mod 端依赖（MTR/Create）尚未初始化。 |
| `ERROR`           | 其他未捕获异常。                     |

## 4. 示例

### Ping 检查

**请求**

```json
{
  "protocolVersion": 1,
  "requestId": "ping-001",
  "action": "beacon:ping",
  "payload": { "echo": "bukkit" }
}
```

**响应**

```json
{
  "protocolVersion": 1,
  "requestId": "ping-001",
  "result": "OK",
  "message": "",
  "payload": {
    "echo": "bukkit",
    "receivedAt": 1733616000000,
    "latencyMs": 12
  }
}
```

### 获取 MTR 数据（示例占位）

```json
{
  "protocolVersion": 1,
  "requestId": "mtr-routes",
  "action": "mtr:get_routes",
  "payload": {
    "dimension": "mtr:hk",
    "limit": 100
  }
}
```

返回体结构由 Mod 的 `BeaconActionHandler` 定义，推荐包含 `routes`, `stations`, `depots` 等字段。

## 5. 实现建议

1. **限流与队列**：若 Bukkit 可能并发 1000+ 请求，建议自定义 `requestId` 并维护超时队列，5s 未收到响应即认为失败；Mod 端可按 `requestId` 原样回传以实现“伪 socket.io”语义。
2. **序列化**：统一使用 UTF-8 JSON，避免 Bukkit/Mod 出现 GZip 或 BinaryTag 的兼容问题。若需压缩，可在 payload 内嵌 `base64` 数据。
3. **版本迁移**：当协议升级时递增 `protocolVersion`，并保持旧版本 handler 在一定时间内仍可工作，方便平滑升级 Bukkit 端。
4. **安全校验**：可以在 `payload` 中附带服务器签名或鉴权 token，Mod 端验证通过后再执行可能影响游戏存档的操作。

## 7. 请求-响应与并发模型

- **一问一答**：Plugin Messaging Channel 更像“轻量 RPC”而非 Vue/Pinia 这类状态管道。Bukkit 发送一条 JSON 请求后，Mod 按 `requestId` 处理并回传唯一响应，不会自动推送更多内容。
- **为何强制 `requestId`**：通道自身不带 session 或 ack；如果 Bukkit 在 1 tick 内投递多条消息，只有靠 `requestId` 才能把返回结果与 Future/Promise 关联，避免乱序。
- **并发行为**：Minecraft 主线程会按照收到的顺序依次处理消息，极端场景（>1000 条）只会增加排队时间，不会丢失数据。可结合 `BUSY`/`ERROR` 结果码与客户端超时重试策略构建更健壮的链路。
- **扩展能力**：每种功能都由一个 `action` 标识驱动。例如要新增 `mtr:get_routes`，只需在 Mod 侧实现对应的 `BeaconActionHandler` 并在 Bukkit 请求中填入该 `action`。
- **更多 action 文档**：参考 `docs/Beacon Actions.md` 获取可用 action 列表与字段定义。

## 6. Bukkit 监听示例

```java
public class BeaconChannelListener implements PluginMessageListener {
    private final Gson gson = new Gson();

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"hydroline:beacon_provider".equals(channel)) {
            return;
        }
        JsonObject json = gson.fromJson(new String(message, StandardCharsets.UTF_8), JsonObject.class);
        String requestId = json.get("requestId").getAsString();
        String result = json.get("result").getAsString();
        // TODO: resolve pending futures by requestId
    }
}
```

> 以上示例仅示意如何匹配 `requestId`，实际项目可以用 `CompletableFuture` 或 `Promise` 映射，保证 1000 条并发时仍能按顺序拿到响应。
