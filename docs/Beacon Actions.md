# Beacon Actions

记录 Beacon Provider 目前可用的 `action` 与其输入/输出格式，并说明如何扩展新的 action，方便 Bukkit 插件或其他外部系统调用。

> 注意：Beacon Provider Mod 只会在服务端（Fabric/Forge）加载，所有 action 的执行与数据采集都发生在服务端主线程，Bukkit 通过 Plugin Messaging Channel 发送请求，再由服务端转回响应。

## 1. Action 列表

| Action 名称                 | 作用                                                                                           | 请求 `payload`                                                                                    | 响应 `payload`                                                                                                                                                        |
| --------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `beacon:ping`               | 校验通道连通性并测量延迟。                                                                     | 可选字段：`echo` (`string`) 将被原样返回。                                                        | `echo`（原样返回）、`receivedAt`（服务器时间戳，ms）、`latencyMs`（处理耗时估算）。                                                                                   |
| `beacon:invoke`             | 预留的默认 action，用于兼容旧版或一次性调试；当前未绑定具体实现，发送会收到 `INVALID_ACTION`。 | 任意 JSON。                                                                                       | -                                                                                                                                                                     |
| `mtr:list_network_overview` | 汇总所有维度的线路、车站、车厂、收费区概览，供网站首页展示。                                   | `{}` 或 `{ "dimension": "minecraft:overworld" }`（可选过滤维度）。                                | `{ "dimensions": [ { "dimension": "...", "routes": [...], "depots": [...], "fareAreas": [...] } ] }`                                                                  |
| `mtr:get_route_detail`      | 查询指定维度 + 线路的节点序列、颜色、类型等信息，用于线路详情页。                              | `{ "dimension": "minecraft:overworld", "routeId": 123 }`                                          | `{ "dimension": "...", "routeId": 123, "name": "...", "color": 16711680, "routeType": "NORMAL", "nodes": [...] }`                                                     |
| `mtr:list_depots`           | 返回所有车厂的排班、关联线路和实时/循环配置。                                                  | `{}` 或 `{ "dimension": "minecraft:overworld" }`                                                  | `{ "depots": [ { "depotId": 1, "name": "...", "routeIds": [...], "departures": [...], ... } ] }`                                                                      |
| `mtr:list_fare_areas`       | 输出站点/收费区边界（Leaflet 绘制用），含换乘线路。                                            | `{ "dimension": "minecraft:overworld" }`                                                          | `{ "dimension": "...", "fareAreas": [ { "stationId": 1, "zone": 3, "bounds": {...}, "interchangeRouteIds": [...] } ] }`                                               |
| `mtr:list_nodes_paginated`  | 为 Bukkit 端批量同步节点（含轨道类型、是否站台段），供地图绘制。                               | `{ "dimension": "minecraft:overworld", "cursor": "optional", "limit": 512 }`                      | `{ "dimension": "...", "nodes": [ { "x": 0, "y": 64, "z": 0, "railType": "PLATFORM", ... } ], "nextCursor": "...", "hasMore": true }`                                 |
| `mtr:get_station_timetable` | 查询指定站（可选限定站台）的计划到站＋延误，供站点详情页使用。                                 | `{ "dimension": "minecraft:overworld", "stationId": 456, "platformId": 789 }` (`platformId` 可选) | `{ "dimension": "...", "stationId": 456, "platforms": [ { "platformId": 789, "entries": [ { "routeId": 1, "arrivalMillis": 123456789, "delayMillis": 12000 } ] } ] }` |
| `mtr:list_stations`         | 导出所有站点、站台、可换乘线路及坐标范围，供网站缓存和 Leaflet 渲染。                         | `{}` 或 `{ "dimension": "minecraft:overworld" }`                                                  | `{ "dimension": "...?", "stations": [ { "stationId": 1, "name": "...", "zone": 3, "bounds": {...}, "interchangeRouteIds": [...], "platforms": [ { "platformId": 10, "name": "...", "routeIds": [...], "depotId": 5 } ] } ] }` |
| `mtr:get_route_trains`      | 查询指定线路下的实时列车状态（UUID、位置、当前/下一站、延误等）。                              | `{ "dimension": "minecraft:overworld", "routeId": 123 }`                                          | `{ "dimension": "...", "routeId": 123, "trains": [ { "trainUuid": "...", "transportMode": "TRAIN", "currentStationId": 1, "nextStationId": 2, "segmentCategory": "PLATFORM", "progress": 0.5, "delayMillis": 12000, "node": {...} } ] }` |
| `mtr:get_depot_trains`      | 查询指定车厂内列车的运行状态，与 `mtr:get_route_trains` 返回结构一致。                          | `{ "dimension": "minecraft:overworld", "depotId": 9 }`                                            | `{ "dimension": "...", "depotId": 9, "trains": [ ... ] }`                                                                                                                        |

> 说明：我们会在后续版本中补充更多 MTR/Create 相关业务 action，并在该文档持续更新，同时保持 `payload` / `result` 兼容。

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
