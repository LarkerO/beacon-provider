# Client HTTP API

为了配合外部工具获取客户端运行时信息，本模组在客户端（Client Side）启动了一个轻量级 HTTP 服务器。

## 1. 服务端信息

- **监听端口**：`45210`（若占用则递增尝试至 `45220`）
- **协议**：HTTP/1.1
- **安全性**：响应体使用 SM2 算法加密（C1C3C2 模式，Base64 编码）

## 2. 接口列表

### 获取客户端信息

**请求**

- **URL**: `/api/getClientInfo`
- **Method**: `GET`

**响应**

- **Content-Type**: `text/plain`
- **Body**: Base64 编码的 SM2 密文

**解密后的 JSON 结构示例**

```json
{
  "java_home": "C:\\Program Files\\Java\\jdk-17",
  "java_version": "17.0.1",
  "java_vendor": "Oracle Corporation",
  "name": "Steve",
  "token": "eyJhbGciOi...",
  "uuid": "84827052-1627-4180-877f-172547053075",
  "memory_max": 4294967296,
  "memory_total": 1073741824,
  "memory_free": 536870912,
  "memory_used": 536870912
}
```

**字段说明**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `java_home` | string | 当前 Java 运行时路径 |
| `java_version` | string | Java 版本 |
| `java_vendor` | string | Java 供应商 |
| `name` | string | **玩家游戏名**（Minecraft 角色名） |
| `token` | string | **认证 Token**（Session Access Token，敏感信息） |
| `uuid` | string | 玩家 UUID |
| `memory_max` | long | JVM 最大内存 (bytes) |
| `memory_total` | long | JVM 当前已申请内存 (bytes) |
| `memory_free` | long | JVM 当前空闲内存 (bytes) |
| `memory_used` | long | JVM 当前已用内存 (bytes) |

## 3. 加密与解密

**加密参数**

- **算法**: SM2
- **模式**: C1C3C2
- **曲线**: `sm2p256v1`
- **密钥**: 响应数据使用硬编码的私钥对应的**公钥**进行加密。外部工具需持有对应的**私钥**进行解密。（注：此处遵循需求描述，虽然通常公钥加密私钥解密，但由于需求指定私钥在服务端代码中，故服务端使用其派生的公钥加密，确保只有知晓私钥的一方能解密）

**硬编码私钥 (Hex)**: `55b23d5e236526e8576404285743210141315574512415152341241512512341`

**解密示例 (Java/BouncyCastle)**

```java
// 1. 解析 Base64
byte[] cipherText = Base64.getDecoder().decode(responseBody);

// 2. 初始化 SM2 解密引擎
SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
ECPrivateKeyParameters privateKey = ...; // 使用上述私钥构造
engine.init(false, privateKey);

// 3. 解密
byte[] plainText = engine.processBlock(cipherText, 0, cipherText.length);
String json = new String(plainText, StandardCharsets.UTF_8);
```
