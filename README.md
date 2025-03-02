#伙伴匹配系统
## 项目概述
伙伴匹配系统旨在实现一个高效、精准的伙伴匹配系统，为用户提供优质的匹配服务。本项目涵盖了从后端开发到缓存设计、分布式锁实现以及数据抓取等多个关键技术点，以确保系统的稳定性和性能。

## 项目主要内容

### 1. 匹配算法
在项目开发的后端部分，随机匹配模块采用了多种算法来实现精准匹配：
- **编辑距离算法（Levenshtein Distance）**：最小编辑距离是指字符串 1 通过最少多少次增删改字符的操作可以变成字符串 2。详细信息可参考[详解编辑距离算法 - Levenshtein Distance - CSDN 博客](https://blog.csdn.net/dbc_121/article/details/104198838)。
- **余弦相似度算法（带权重计算）**：如果需要带权重计算，比如学什么方向最重要，性别相对次要，可使用余弦相似度算法。相关信息可参考[相似度算法——余弦相似度（附带 Java 现实）_余弦相似度 java - CSDN 博客](https://blog.csdn.net/qq_36488175/article/details/109787805)。


### 2. Redis 相关
#### 2.1 Redis 数据结构
Redis 在本项目中作为重要的缓存工具，其支持多种数据结构：
- **String 字符串类型**：例如 `name:"chenxing"`。
- **List 列表**：如 `names:["chenxing","chenxingxing","chenxing"]`。
- **Set 集合**：`names:["chenxing","chenxingxing"]` （值不能重复）。
- **Hash 哈希**：`nameAge:{ "chenxing":1,"chenxingxing":2 }` （键不能重复）。
- **Zset 集合**：`names:{chenxing - 9,chenxingxing - 2}` （加入一个分数，从小到大排序，适合排行榜）。

此外，还有一些特殊的数据结构，如：
- **bloomfilter（布隆过滤器）**：主要从大量的数据中快速过滤值，比如邮件黑名单拦截。
- **geo (计算地理位置)**：用于地理位置的计算。
- **hyperloglog(pv/uv)**：可用于统计页面的 PV（Page View）和 UV（Unique Visitor）。
- **pub/sub（发布订阅）**：类似消息队列，实现消息的发布和订阅。
- **BitMap**：如 `001010101010101010101010101`。

#### 2.2 Redis 安装与配置
- **安装**：在 Windows 环境下，可通过(https://github.com/redis-windows/redis-windows) 进行安装。
- **管理工具**：使用[Tiny RDM](https://redis.tinycraft.cc/) 进行 Redis 管理。

#### 2.3 Spring 项目引入 Redis 相关依赖
在 Spring 项目中，引入 Redis 相关依赖以操作 Redis：
```xml
<!-- https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-data-redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <version>2.6.2</version>
</dependency>
```

#### 2.4 设计缓存 Key
不同用户看到的数据不同，因此需要设计合适的缓存 Key，例如：
"BuddyLink:user:recommend:userId"
同时，由于 Redis 内存不能无限增加，需要记得为缓存数据设置过期时间。

### 3. Redisson 实现分布式锁
#### 3.1 定时任务＋锁 - 看门狗机制
Redisson 中提供了续期机制，即看门狗机制：
- 开一个监听线程，如果方法还没执行完，就帮你重置 Redis 锁的过期时间。
- 原理：
    - 监听当前线程，默认过期时间是 30 秒，每 10 秒续期一次（补到 30 秒）。
    - 如果线程挂掉（注意 debug 模式也会被它当成服务器宕机），则不会续期。

## 项目使用
1. 克隆本项目到本地。
2. 按照 Redis 安装与配置部分的说明，安装并配置好 Redis。
3. 在 Spring 项目中引入 Redis 相关依赖。
4. 根据需要，实现相应的匹配算法、分布式锁以及网页信息抓取功能。

## 注意事项
- Redis 内存管理：由于 Redis 内存不能无限增加，在使用 Redis 作为缓存时，务必为缓存数据设置合理的过期时间，以避免内存溢出。
- 分布式锁：在使用 Redisson 实现分布式锁时，注意 debug 模式可能会被看门狗机制当成服务器宕机，从而导致锁不会续期。
