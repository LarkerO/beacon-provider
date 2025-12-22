# MTR API 调研记录（3.2.2-hotfix-2）

本文基于 `libs/mtr3/MTR-forge-1.20.1-3.2.2-hotfix-2-slim.jar` 中暴露的 `mtr.data` 包整理，用于后续在 Beacon Provider 中封装线路、车站、车厂（Depot）以及时刻/延误相关 action。

## 1. 数据入口：`RailwayData`

- 每个维度（`ServerLevel`）都有一个 `RailwayData` 实例，可通过 `RailwayData.getInstance(level)` 获取。
- `RailwayData` 公开以下数据集合，均为 `Set<T>`：`stations`、`platforms`、`sidings`、`routes`、`depots`、`lifts`。
- `dataCache` 提供 ID -> 对象映射，需要在读取前调用 `dataCache.sync()`，之后可通过 `stationIdMap`、`platformIdMap`、`routeIdMap`、`depotIdMap` 等做 O(1) 查询。
- `RailwayData` 自带多个模块：`RailwayDataRouteFinderModule`（换乘/连通查询）、`RailwayDataDriveTrainModule`（列车仿真）、`RailwayDataLoggingModule`（编辑日志）等，可按需深挖。

## 2. 核心实体概览

| 类型       | 继承链                          | 关键字段                                                 | 说明                                                                                    |
| ---------- | ------------------------------- | -------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `Station`  | `AreaBase -> NameColorDataBase` | `zone`、`exits`、`corner1/2`                             | 表示站点及其占用区域、站厅出口等。                                                      |
| `Platform` | `SavedRailBase`                 | 站台两端坐标、`dwellTime`                                | 可通过 `getMidPos()` 定位站台中心。                                                     |
| `Route`    | `NameColorDataBase`             | `routeType`、`platformIds`、`isHidden`                   | `platformIds` 是有序的 `RoutePlatform` 列表，包含 `platformId` 和 `customDestination`。 |
| `Depot`    | `AreaBase`                      | `routeIds`、`platformTimes`、`departures`、`useRealTime` | 可查询/设置发车频率，支持实时/循环调度。                                                |
| `Siding`   | `SavedRailBase`                 | `railLength`、列车配置                                   | 用于连接 Depot 与线路。                                                                 |

> 所有 `NameColorDataBase` 子类共有字段：`id`、`transportMode`（`TransportMode` 枚举）、`name`、`color`。

## 3. 车站 / 线路 / 车厂数据提取

1. **线路（Route）**：

   - `route.routeType`：`RouteType.NORMAL|LIGHT_RAIL|HIGH_SPEED`。
   - `route.platformIds`：按行车顺序排列的站台 ID；可借助 `DataCache.platformIdMap` 还原对应站台，再关联到 `platformIdToStation` 得到车站。
   - 线路可标记为循环（`circularState`）或隐藏（`isHidden`）。

2. **车站（Station）**：

   - 继承 `AreaBase`，可用 `getCenter()` 得到区块中心；`corner1/2` 给出矩形范围。
   - `exits` 为 `Map<String, List<String>>`，键为出口分组（如 Exit A），值为目的地文本。
   - 可通过 `DataCache.stationIdToConnectingStations` 获取换乘关系。

3. **车厂（Depot）**：
   - `routeIds`：车厂负责的线路列表。
   - `platformTimes`：嵌套 `routeId -> platformId -> Float`，表示在该线路上各站的计划停靠时间。
   - `departures`：一天内（MTR 内部 24h 刻度）发车表；`getNextDepartureMillis()`、`getMillisUntilDeploy()` 可推导下一班车。
   - `useRealTime` 与 `repeatInfinitely` 控制是否按现实时间调度以及是否循环。

## 4. 时刻表与延误

- `RailwayData.getSchedulesForStation(Map<Long, List<ScheduleEntry>> target, long stationId)`：填充目标 Map，返回该站所有线路的到站计划；`ScheduleEntry` 含 `arrivalMillis`、`trainCars`、`routeId`、`currentStationIndex`。
- `RailwayData.getSchedulesAtPlatform(long platformId)`：获取单一站台的时刻表列表。
- `RailwayData.getTrainDelays()`：返回延误 Map，结构为 `Map<Long /*routeId*/, Map<BlockPos /*platform*/, TrainDelay>>`。
- `TrainDelay` 提供 `getDelayTicks()`（以 tick 计）和 `getLastDelayTime()`；`isExpired()` 用于过滤旧数据。

## 5. 建议封装的 Action（示例）

| Action 名称                | 请求示例                                                   | 返回要点                                                                                                            |
| -------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `mtr:list_dimensions`      | `{}`                                                       | 列出当前加载的维度及是否存在 `RailwayData`。                                                                        |
| `mtr:get_network_snapshot` | `{ "dimension": "minecraft:overworld" }`                   | 该维度的 `routes`、`stations`、`platforms`、`depots`，以及 Route-Platform、Platform-Station、Depot-Route 的关联表。 |
| `mtr:get_depot_schedule`   | `{ "dimension": "minecraft:overworld", "depotId": 123 }`   | 车厂的 `departures`、`platformTimes`、`useRealTime`、`repeatInfinitely`、`nextDepartureMillis`。                    |
| `mtr:get_station_arrivals` | `{ "dimension": "minecraft:overworld", "stationId": 456 }` | `getSchedulesForStation` 结果 + 匹配到的 `TrainDelay`，并附带线路/站台元信息。                                      |
| `mtr:get_route_trains`     | `{ "dimension": "minecraft:overworld", "routeId": 123 }`   | 报告该维度/线路上的列车状态，包含 `trainUuid`/`trainId`、`railId`、`currentStationId`、`nextStationId`、`delayMillis`。      |
| `mtr:get_station_schedule` | `{ "dimension": "minecraft:overworld", "stationId": 456 }` | 按维度/站台返回时刻表（含 `routeName`、`arrivalMillis`、`currentStationIndex`、`platformName`）。                          |
| `mtr:get_depot_trains`     | `{ "dimension": "minecraft:overworld" }`                   | 列出该维度所有 depot 及其 `departures`/`routeIds`，并附带 `fetchDepotTrains` 返回的列车状态（`trainUuid`、`routeId`、`railId`、`progress`）等。 |
| `mtr:get_all_station_schedules` | `{}`                                              | 返回所有维度所有站点的 station/platform 时刻表，附带平台名称与到站记录（`arrivalMillis`、`routeId`、`trainCars`、`delayMillis`）。 |

> 建议所有返回值只传输需要的字段，避免一次性传输整个 NBT。

## 6. 代码示例（获取网络快照）

```java
public record NetworkSnapshot(ResourceLocation dimension,
										List<Route> routes,
										List<Station> stations,
										List<Depot> depots) {}

public NetworkSnapshot loadSnapshot(ServerLevel level) {
	 RailwayData data = RailwayData.getInstance(level);
	 data.dataCache.sync(); // 确保 ID 映射最新
	 return new NetworkSnapshot(
		  level.dimension().location(),
		  List.copyOf(data.routes),
		  List.copyOf(data.stations),
		  List.copyOf(data.depots)
	 );
}
```

## 7. 后续工作

1. 按上表 action 规范补充 `BeaconActionHandler` 实现，并在 `BeaconServiceFactory` 注册。
2. 设计 JSON schema（推荐使用 ID + 关联表方式）以减少消息体积。
3. 在 Bukkit 端编写集成测试，验证 `station_arrivals`、`depot_schedule` 等 action 返回值与游戏内看板一致。

## 8. 常见问题补充

### 8.1 如何拿到某条线路的完整信息（名称、颜色、节点、关联站点）？

- **线路元数据**：通过 `RailwayData.dataCache.routeIdMap.get(routeId)` 获取 `Route` 实例，可直接读取 `name`、`color`、`transportMode` 等字段。
- **线路内站台顺序**：`Route.platformIds` 是按行车顺序排列的 `RoutePlatform` 列表；对每个 `platformId` 可在 `dataCache.platformIdMap` 查到 `Platform`，再经 `dataCache.platformIdToStation` 反查站点。
- **站点细节**：`Station` 继承 `AreaBase`，提供 `corner1/corner2`（Leaflet 可直接用作左上/右下坐标，需换算方块坐标 -> 世界坐标），`getCenter()` 也能给出中心点。`dataCache.stationIdToConnectingStations` 告诉你该站还能换乘哪些线路/站。
- **线路节点（Rail Node）**：`RailwayData` 内部维护 `rails: Map<BlockPos, Map<BlockPos, Rail>>`，节点即该 Map 的 key（轨道端点）。虽然没有公开 getter，可以：
  1.  借助 `RailwayDataRouteFinderModule`（`railwayDataRouteFinderModule` 字段）调用 `findRoute`，拿到 `RouteFinderData` 列表，每条包含 `pos`（节点 `BlockPos`）、`routeId`、`stationIds`、`duration`，即可重建线路涉及的所有 node；
  2.  或通过 mixin/反射访问 `rails` Map，自行筛选出属于某 `routeId` 的 `Rail`（`Rail` 对象只记录两端 `BlockPos` 与 `RailType`，需结合 `Route.platformIds`/`Siding` 才能归属到具体线路）。
- **可落地方案**：通常做法是在同步 `routes` 时同时输出：`[{ routeId, name, color, platformSequence: [{ platformId, stationId, stationName, bounds }], nodes: [ [x,y,z], ... ] }]`，其中 `nodes` 由 `RouteFinderData` 或 `rails` Map 生成。

### 8.2 能否获取某站点/站台的时刻表？

可以，`RailwayData` 已经内置以下方法：

1. `getSchedulesForStation(Map<Long, List<ScheduleEntry>> target, long stationId)`：会把目标站涉及的全部线路写入 `target`。`ScheduleEntry` 包含 `arrivalMillis`、`routeId`、`trainCars`、`currentStationIndex`，可据此展示整站的到站时间。
2. `getSchedulesAtPlatform(long platformId)`：直接返回单一站台（即站台编号）上的时刻表列表，最适合“指定站台”场景。
3. 若需要延误信息，同时读取 `getTrainDelays()`（`routeId -> BlockPos -> TrainDelay`），用站台的 `midPos` 转为 `BlockPos` 进行匹配，即可在原始时刻表上叠加延误秒数。

Beacon Provider 输出的 schedule JSON 结构会额外把 `routeName`、`route`（线路标记）、`color`（十进制）、`destination`、`circular`、`delayMillis` 等字段附加到每个 `ScheduleEntry` 上，客户端可以直接用这些字段渲染到站时间、线路标签、延误（`delayMillis`）和色彩。`mtr:get_station_schedule` 与 `mtr:get_all_station_schedules` 就是把这套内容打包发送给前端。

### 8.3 是否存在可识别每列车的唯一信息？能否定位列车？

- **唯一标识**：`Train` 基类里有 `trainId`（字符串，通常来自侧线设置）、`sidingId`、`transportMode` 等字段，用于识别列车实例；若需要完全唯一，可组合 `trainId + depotId` 或直接给列车附加 UUID（`TrainServer.writeTrainPositions` 中就使用了 `UUID` 作为每节车厢/实体的 key）。
- **列车位置**：`Train` 内部维持 `path: List<PathData>` 和 `railProgress`（double），`TrainServer` 在 `simulateTrain` 时会调用 `writeTrainPositions(List<Map<UUID, Long>>, SignalBlocks)`，把每辆车当前所处的轨道节点（`BlockPos` 序列编码成 `long`）写入 `RailwayData.trainPositions`。这些字段是私有的，想要读位置必须：
  1.  通过 mixin/反射访问 `RailwayData.trainPositions` 或挂钩 `TrainServer.writeTrainPositions`，将 `UUID -> BlockPos` 同步到自定义缓存；
  2.  或监听 `UpdateNearbyMovingObjects<TrainServer>`（`RailwayData.updateNearbyTrains`）在每 tick 推送的可见列车集合，从 `TrainServer` 实例上读取 `getRailProgress()`、`path`、`nextPlatformIndex`。
- **运行区间**：`path` 中的 `PathData` 已经划分出轨道节点序列，你可以根据 `railProgress` 与 `nextPlatformIndex` 判断列车位于哪一段轨道、距离下一个站还有多少距离。若只需站点级别定位，直接读取 `getThisStation()` / `getNextStation()`（`TrainClient` 暴露的客户端方法，可通过同步包在服务端复用）即可。
- **总结**：MTR 自身没有“现成 HTTP API”，但列车 ID、路径和位置数据全部存在，只是需要通过 mixin 或模块钩子把它们抛到你的 `BeaconActionHandler` 中再转发到 Bukkit。
