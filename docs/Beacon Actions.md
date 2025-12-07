# Beacon Actions

记录 Beacon Provider 目前可用的 `action` 与其输入/输出格式，并说明如何扩展新的 action，方便 Bukkit 插件或其他外部系统调用。

## 1. Action 列表

| Action 名称     | 作用                                                                                           | 请求 `payload`                             | 响应 `payload`                                                                      |
| --------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------ | ----------------------------------------------------------------------------------- |
| `beacon:ping`   | 校验通道连通性并测量延迟。                                                                     | 可选字段：`echo` (`string`) 将被原样返回。 | `echo`（原样返回）、`receivedAt`（服务器时间戳，ms）、`latencyMs`（处理耗时估算）。 |
| `beacon:invoke` | 预留的默认 action，用于兼容旧版或一次性调试；当前未绑定具体实现，发送会收到 `INVALID_ACTION`。 | 任意 JSON。                                | -                                                                                   |

> 说明：我们会在后续版本中补充 MTR/Create 相关的业务 action（例如 `mtr:get_routes`、`create:get_machines`），并在该文档持续更新。

## 2. 新增 action 的步骤

1. **实现 Handler**：在 `common` 内创建实现 `BeaconActionHandler` 的类，例如：

   ```java
   public final class MtrGetRoutesHandler implements BeaconActionHandler {
       @Override
       public String action() {
           return "mtr:get_routes";
       }

       @Override
       public BeaconResponse handle(BeaconMessage message, PluginMessageContext context) {
           // 解析 payload，调用 MTR API，组装返回体
           return BeaconResponse.builder(message.getRequestId())
               .payload(routesJson)
               .build();
       }
   }
   ```

2. **注册 Handler**：
   - 如果是跨 loader 公用的 handler，可在 `BeaconServiceFactory#createDefault()` 中 `register(new MtrGetRoutesHandler())`；
   - 如需 loader 特有依赖（Fabric/Forge），则在对应 loader 项目初始化 `DefaultBeaconProviderService` 后调用 `.register(...)`。
3. **约定 schema**：在本文件追加条目，写明请求/响应字段，方便 Bukkit 端实现序列化/反序列化。
4. **Bukkit 调用**：在请求 JSON 中将 `action` 设置为新名称，`payload` 遵循约定即可。响应会沿用同一 `requestId` 返回。

## 3. 命名与兼容性建议

- 使用 `<domain>:<verb>` 结构（如 `mtr:get_routes`），便于分类；`beacon:` 命名空间留给框架级 action。
- 当 action 需要升级 schema 时，可在 `payload` 中加入 `version` 字段，或新增 `action` 名称以保持向后兼容。
- 如果 action 需要多次推送大量数据，建议拆分为分页请求，并在响应中返回 `hasMore` / `cursor` 等字段；Plugin Messaging Channel 仍是“请求-响应”语义。
