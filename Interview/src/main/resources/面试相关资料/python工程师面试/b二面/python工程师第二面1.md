Python工程师技术二面 - 详细面试文档

基本信息

面试岗位：Python开发工程师



面试轮次：二面（技术深度面）



面试时长：60-75分钟



目标公司：对标腾讯T4/T5、阿里P7/P8、字节2-2/3-1级别



考察重点：系统设计能力、架构思维、复杂问题解决、技术选型权衡、高并发处理、源码理解深度



第一部分：项目深度挖掘与架构设计 (15分钟)

问题1：请详细介绍你做过的最有技术挑战的项目，包括系统架构、数据流向、你解决了哪些核心难点？

考察意图：



对系统整体架构的把握能力



技术深度和问题解决能力



在团队中的角色和贡献



表达和逻辑能力



评分标准：



好 (8-10分)：能画出架构图（口头描述清晰），说明核心模块和数据流向，具体描述2-3个技术难点及解决方案，有数据支撑优化效果



中 (5-7分)：能说清项目功能和自己的模块，但缺乏整体视角，难点描述泛泛而谈



差 (0-4分)：逻辑混乱，只罗列功能，说不出技术难点



参考答案要点：



text

我最近主导的项目是【电商订单中心重构】。背景是原单体订单系统在双11大促时扛不住峰值流量，经常出现接口超时、数据库连接池爆满等问题。



\*\*系统架构：\*\*

客户端 → 接入层(Nginx) → API网关 → 订单服务集群(多实例) → \[缓存(Redis) | 数据库(MySQL分库分表) | 消息队列(RabbitMQ)]

↓ ↓ ↓

静态资源CDN 鉴权/限流 异步任务(库存/积分/通知)



text



\*\*核心模块：\*\*

1\. 订单读写分离：写操作主库，读操作从库+缓存

2\. 订单状态机：待支付→已支付→已发货→已完成→已取消

3\. 分布式锁：防止重复下单、超卖

4\. 异步通知：下单成功后发MQ，多个消费者并行处理



\*\*我解决的核心难点：\*\*



\*\*难点1：超卖问题\*\*

\- 现象：并发下单时，库存扣减出现负数

\- 排查：并发请求同时读到库存=1，都执行扣减

\- 解决：Redis分布式锁 + Lua脚本原子操作

```python

\# Lua脚本保证原子性

lua = """

local stock = redis.call('get', KEYS\[1])

if stock and tonumber(stock) >= tonumber(ARGV\[1]) then

&nbsp;   redis.call('decrby', KEYS\[1], ARGV\[1])

&nbsp;   return 1

end

return 0

"""

result = redis.eval(lua, 1, f"stock:{product\_id}", quantity)

if result:

&nbsp;   # 继续下单流程

&nbsp;   mq.send("order\_create", order\_data)

else:

&nbsp;   return "库存不足"

难点2：深度分页查询慢



现象：订单列表翻到第100页时，接口响应超过5秒



排查：LIMIT 1000000, 20 扫描大量无用数据



解决：游标分页（基于上一页最后ID）



sql

-- 优化前

SELECT \* FROM orders WHERE user\_id=123 ORDER BY id DESC LIMIT 1000000, 20;



-- 优化后（记录上一页最后ID=1000000）

SELECT \* FROM orders WHERE user\_id=123 AND id < 1000000 ORDER BY id DESC LIMIT 20;

难点3：消息积压导致订单状态更新延迟



现象：大促时订单创建消息积压几十万，用户付完款很久才看到状态更新



排查：消费者处理慢，单个消费者处理一条消息要200ms



解决：多分区 + 多消费者并行消费 + 批量处理



python

\# 生产者按订单ID路由到不同分区

partition = order\_id % 10

kafka.send("order\_events", value=order\_data, partition=partition)



\# 10个消费者并行消费不同分区

for i in range(10):

&nbsp;   consumer = KafkaConsumer("order\_events", partition=i)

&nbsp;   # 每个消费者单线程处理，保证分区内有序

优化效果：



接口P99响应时间从800ms降到150ms



系统扛住了双11峰值10万QPS



数据库CPU使用率从90%降到40%



text



\*\*追问点：\*\*

\- 如果现在让你重新设计，会有什么不同？

\- 分库分表怎么做的？分片键怎么选的？

\- 监控和告警怎么做的？



---



\## \*\*第二部分：高并发系统设计 (15分钟)\*\*



\### \*\*问题2：设计一个秒杀系统，需要考虑哪些问题？从架构层面详细说明你的设计方案\*\*



\*\*考察意图：\*\*

\- 高并发系统设计能力

\- 流量削峰思路

\- 缓存和消息队列的合理运用

\- 数据一致性保障



\*\*评分标准：\*\*

\- \*\*好\*\*：能完整描述秒杀系统的各个层次（前端拦截、网关限流、缓存扣库存、MQ削峰、DB最终落单），说明每个环节的作用和选型理由，能处理超卖、防重等问题

\- \*\*中\*\*：能说出一部分（如Redis扣库存），但缺乏整体架构思维

\- \*\*差\*\*：只说“加缓存”，说不出具体实现



\*\*参考答案：\*\*

\*（摘自后端场景面试题文档-如何设计一个秒杀功能）\*



\*\*秒杀系统核心挑战：\*\*

1\. \*\*瞬时高并发\*\*：瞬间流量可能是平时的1000倍

2\. \*\*超卖风险\*\*：库存不能扣成负数

3\. \*\*防刷\*\*：防止机器脚本抢购

4\. \*\*用户体验\*\*：不能直接崩溃，要友好提示



\*\*分层架构设计：\*\*

【前端层】

↓

【接入层/Nginx】

↓

【网关层/应用层】

↓

【缓存层/Redis】

↓

【消息队列】

↓

【数据库层】



text



\*\*第一层：前端拦截\*\*

```html

<!-- 1. 按钮置灰，防止重复点击 -->

<button onclick="seckill()" id="buyBtn" disabled>抢购中...</button>



<!-- 2. 静态化页面，CDN加速 -->

<!-- 3. 验证码，防机器 -->

第二层：Nginx接入层限流



nginx

\# nginx.conf

limit\_req\_zone $binary\_remote\_addr zone=seckill:10m rate=10r/s;



location /seckill {

&nbsp;   limit\_req zone=seckill burst=20 nodelay;  # 每秒最多10个请求，突发最多20

&nbsp;   proxy\_pass http://backend\_servers;

}

第三层：网关层/应用层限流



python

\# 基于Redis的分布式限流

def rate\_limit(user\_id, api\_name, limit=5, window=1):

&nbsp;   key = f"rate:{api\_name}:{user\_id}:{int(time.time()/window)}"

&nbsp;   count = redis.incr(key)

&nbsp;   if count == 1:

&nbsp;       redis.expire(key, window + 1)

&nbsp;   return count <= limit



\# 使用

@api\_route('/seckill')

def seckill\_api(request):

&nbsp;   if not rate\_limit(request.user\_id, 'seckill', limit=3, window=1):

&nbsp;       return {"code": 429, "message": "操作太频繁"}

&nbsp;   # 继续处理

第四层：Redis扣减库存（核心！）



python

\# 准备工作：预热库存到Redis

redis.set(f"stock:{product\_id}", total\_stock)



\# Lua脚本保证原子扣减

def decr\_stock(product\_id, quantity):

&nbsp;   lua = """

&nbsp;   local stock = redis.call('get', KEYS\[1])

&nbsp;   if not stock then

&nbsp;       return -1  -- 商品不存在

&nbsp;   end

&nbsp;   if tonumber(stock) >= tonumber(ARGV\[1]) then

&nbsp;       redis.call('decrby', KEYS\[1], ARGV\[1])

&nbsp;       return 1   -- 扣减成功

&nbsp;   else

&nbsp;       return 0   -- 库存不足

&nbsp;   end

&nbsp;   """

&nbsp;   result = redis.eval(lua, 1, f"stock:{product\_id}", quantity)

&nbsp;   return result



@api\_route('/seckill')

def seckill\_api(request):

&nbsp;   # 1. 限流检查

&nbsp;   # 2. 用户是否已抢过（Redis Set去重）

&nbsp;   if redis.sismember(f"seckill:{product\_id}:users", request.user\_id):

&nbsp;       return {"code": 400, "message": "已抢过"}

&nbsp;   

&nbsp;   # 3. 扣减库存（原子操作）

&nbsp;   result = decr\_stock(request.product\_id, 1)

&nbsp;   if result <= 0:

&nbsp;       return {"code": 400, "message": "库存不足"}

&nbsp;   

&nbsp;   # 4. 记录已抢用户

&nbsp;   redis.sadd(f"seckill:{product\_id}:users", request.user\_id)

&nbsp;   

&nbsp;   # 5. 发送消息队列异步落单

&nbsp;   mq.send("seckill\_orders", {

&nbsp;       "user\_id": request.user\_id,

&nbsp;       "product\_id": request.product\_id,

&nbsp;       "time": time.time()

&nbsp;   })

&nbsp;   

&nbsp;   return {"code": 0, "message": "抢购成功，正在处理"}

第五层：消息队列削峰填谷



python

\# 消费者 - 匀速处理，每批100条

@kafka\_consumer("seckill\_orders", batch\_size=100)

def process\_seckill\_orders(messages):

&nbsp;   orders = \[]

&nbsp;   for msg in messages:

&nbsp;       orders.append({

&nbsp;           "order\_id": generate\_order\_id(),

&nbsp;           "user\_id": msg\['user\_id'],

&nbsp;           "product\_id": msg\['product\_id'],

&nbsp;           "status": "pending",

&nbsp;           "create\_time": msg\['time']

&nbsp;       })

&nbsp;   

&nbsp;   # 批量插入数据库

&nbsp;   db.batch\_insert("orders", orders)

&nbsp;   

&nbsp;   # 发送成功通知（可选）

&nbsp;   for order in orders:

&nbsp;       mq.send("notifications", {

&nbsp;           "user\_id": order\['user\_id'],

&nbsp;           "message": f"抢购成功，订单号{order\['order\_id']}"

&nbsp;       })

第六层：数据库最终落盘



sql

-- 订单表设计

CREATE TABLE seckill\_orders (

&nbsp;   id BIGINT PRIMARY KEY AUTO\_INCREMENT,

&nbsp;   order\_id VARCHAR(32) UNIQUE,  -- 全局唯一

&nbsp;   user\_id INT NOT NULL,

&nbsp;   product\_id INT NOT NULL,

&nbsp;   status TINYINT DEFAULT 0,  -- 0:待支付 1:已支付 2:已取消

&nbsp;   create\_time DATETIME,

&nbsp;   INDEX idx\_user (user\_id),

&nbsp;   INDEX idx\_product (product\_id)

);

防超卖完整流程：



text

用户请求 → Nginx限流 → 应用层限流 → Redis原子扣库存（成功才继续） → 发MQ → 返回成功

&nbsp;                                     ↓

&nbsp;                                Redis记录已抢用户

为什么不会超卖？



Redis扣库存是原子操作（Lua保证）



库存扣减成功才进入后续流程



数据库只是最终落盘，不参与实时扣减



为什么能扛高并发？



Nginx限流过滤大部分请求



Redis单机10万+ QPS扛住瞬时流量



消息队列削峰，数据库匀速写入



追问点：



如果Redis挂了怎么办？

（答：集群+主从，库存预热时也要考虑）



用户抢到后不支付怎么办？

（答：订单设置过期时间，超时释放库存）



怎么防止恶意用户刷单？

（答：IP限流、设备指纹、用户行为分析）



问题3：如何设计一个订单超时取消功能？订单30分钟未支付自动取消

考察意图：



延迟任务处理能力



消息队列的深度应用



方案选型权衡



评分标准：



好：能给出2-3种方案（RabbitMQ死信队列、RocketMQ延迟消息、Redis ZSet轮询），对比优缺点，说明选型理由



中：只知一种方案，说不出原理



差：说“用定时任务每分钟扫一次”



参考答案：

（摘自RabbitMQ面试题文档-如何实现延迟消息、后端场景-订单超时取消功能设计）



方案1：RabbitMQ死信队列（最常用）



python

\# 架构：订单创建 → 发消息到TTL队列 → 30分钟过期 → 进入死信队列 → 消费取消



\# 1. 配置队列和死信交换机

import pika



connection = pika.BlockingConnection(...)

channel = connection.channel()



\# 声明死信交换机

channel.exchange\_declare(

&nbsp;   exchange='dlx.exchange',

&nbsp;   exchange\_type='direct',

&nbsp;   durable=True

)



\# 声明死信队列

channel.queue\_declare(

&nbsp;   queue='dlx.queue',

&nbsp;   durable=True

)

channel.queue\_bind('dlx.queue', 'dlx.exchange', 'cancel')



\# 声明业务队列，设置TTL和死信转发

args = {

&nbsp;   'x-message-ttl': 1800000,  # 30分钟，单位毫秒

&nbsp;   'x-dead-letter-exchange': 'dlx.exchange',

&nbsp;   'x-dead-letter-routing-key': 'cancel'

}

channel.queue\_declare(

&nbsp;   queue='order.delay.queue',

&nbsp;   durable=True,

&nbsp;   arguments=args

)



\# 2. 创建订单时发送延迟消息

def create\_order(order\_data):

&nbsp;   # 保存订单到数据库

&nbsp;   order\_id = db.insert("orders", {

&nbsp;       "user\_id": order\_data\['user\_id'],

&nbsp;       "product\_id": order\_data\['product\_id'],

&nbsp;       "status": "pending",  # 待支付

&nbsp;       "create\_time": time.time()

&nbsp;   })

&nbsp;   

&nbsp;   # 发送延迟消息

&nbsp;   channel.basic\_publish(

&nbsp;       exchange='',

&nbsp;       routing\_key='order.delay.queue',

&nbsp;       body=json.dumps({"order\_id": order\_id}),

&nbsp;       properties=pika.BasicProperties(

&nbsp;           delivery\_mode=2  # 持久化

&nbsp;       )

&nbsp;   )

&nbsp;   return order\_id



\# 3. 消费者监听死信队列，处理超时订单

def cancel\_order\_callback(ch, method, properties, body):

&nbsp;   data = json.loads(body)

&nbsp;   order\_id = data\['order\_id']

&nbsp;   

&nbsp;   # 查询订单状态

&nbsp;   order = db.get("SELECT status FROM orders WHERE id=%s", order\_id)

&nbsp;   

&nbsp;   # 如果还是待支付，则取消

&nbsp;   if order and order\['status'] == 'pending':

&nbsp;       db.execute("UPDATE orders SET status='cancelled' WHERE id=%s", order\_id)

&nbsp;       print(f"订单{order\_id}超时取消")

&nbsp;       # 可选：释放库存

&nbsp;       redis.incr(f"stock:{order\['product\_id']}")

&nbsp;   

&nbsp;   ch.basic\_ack(delivery\_tag=method.delivery\_tag)



channel.basic\_consume('dlx.queue', cancel\_order\_callback)

优点：



消息不丢失（持久化）



精度可控（秒级）



成熟稳定



缺点：



每个订单一条消息，量大时MQ压力大



需要配置死信队列，稍复杂



方案2：RocketMQ延迟消息（最直接）



python

\# RocketMQ内置18个延迟等级（1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h）



\# 发送延迟消息

def create\_order(order\_data):

&nbsp;   order\_id = save\_order(order\_data)

&nbsp;   

&nbsp;   msg = Message('order\_topic')

&nbsp;   msg.set\_keys(str(order\_id))

&nbsp;   msg.set\_body(json.dumps({"order\_id": order\_id}).encode())

&nbsp;   msg.set\_delay\_time\_level(14)  # 等级14对应30分钟（需查文档确认）

&nbsp;   

&nbsp;   producer.send\_oneway(msg)

&nbsp;   return order\_id



\# 消费者

@RocketMQListener(topic='order\_topic')

class OrderTimeoutListener:

&nbsp;   def consume(self, msg):

&nbsp;       data = json.loads(msg.body.decode())

&nbsp;       order\_id = data\['order\_id']

&nbsp;       

&nbsp;       # 检查并取消订单

&nbsp;       cancel\_if\_pending(order\_id)

优点：



代码简单，不需要配置死信



性能好，延迟精度高



缺点：



只能使用预设等级，不能自定义任意时间



需要RocketMQ支持



方案3：Redis ZSet轮询（适合中小规模）



python

\# 订单创建时写入ZSet

def create\_order(order\_data):

&nbsp;   order\_id = save\_order(order\_data)

&nbsp;   

&nbsp;   # 过期时间戳 = 当前时间 + 30分钟

&nbsp;   expire\_time = time.time() + 30 \* 60

&nbsp;   redis.zadd("pending\_orders", {str(order\_id): expire\_time})

&nbsp;   return order\_id



\# 定时任务（每分钟执行）

def check\_timeout\_orders():

&nbsp;   now = time.time()

&nbsp;   

&nbsp;   # 取出已过期的订单ID

&nbsp;   expired = redis.zrangebyscore("pending\_orders", 0, now)

&nbsp;   

&nbsp;   for order\_id in expired:

&nbsp;       # 检查订单状态（可能已被支付）

&nbsp;       order = db.get("SELECT status FROM orders WHERE id=%s", order\_id)

&nbsp;       if order and order\['status'] == 'pending':

&nbsp;           db.execute("UPDATE orders SET status='cancelled' WHERE id=%s", order\_id)

&nbsp;           # 释放库存等

&nbsp;       

&nbsp;       # 从ZSet移除

&nbsp;       redis.zrem("pending\_orders", order\_id)



\# 用APScheduler或Celery beat每分钟执行一次

scheduler.add\_job(check\_timeout\_orders, 'interval', minutes=1)

优点：



实现简单，不依赖MQ



Redis性能好



缺点：



精度1分钟，可能延迟



订单量大时ZSet可能很大



需要处理分布式任务重复执行



方案4：定时任务扫表（不推荐）



python

\# 不推荐！每小时扫一次全表，数据量大时灾难

@cron('0 \* \* \* \*')  # 每小时

def scan\_orders():

&nbsp;   # 扫描所有待支付且超过30分钟的订单

&nbsp;   db.execute("""

&nbsp;       UPDATE orders SET status='cancelled' 

&nbsp;       WHERE status='pending' AND create\_time < NOW() - INTERVAL 30 MINUTE

&nbsp;   """)

问题：



全表扫描，数据库压力大



只能每小时一次，延迟高



更新时间不准



方案对比：



方案	精度	可靠性	复杂度	适用规模

RabbitMQ死信队列	秒级	高	中	中大型

RocketMQ延迟消息	秒级	高	低	中大型

Redis ZSet轮询	分钟级	中	低	中小型

定时任务扫表	小时级	低	低	小型（不推荐）

最终推荐：



中小项目用Redis ZSet（简单够用）



大项目用RocketMQ（功能完善）



已有RabbitMQ生态用死信队列



追问点：



如果消息丢了怎么办？

（答：MQ持久化+ACK，或加DB记录补偿）



如果订单已支付，超时消息才到，怎么处理？

（答：幂等检查，状态不是pending就不处理）



第三部分：Redis深度应用 (12分钟)

问题4：Redis的Zset底层实现原理是什么？为什么用跳表而不是红黑树？

考察意图：



对Redis源码的理解



数据结构的选型权衡



对跳表原理的掌握



评分标准：



好：能说清Zset的两种编码（ziplist和skiplist），解释跳表的结构（多层索引、随机层高），对比红黑树和B+树的优缺点，说明Redis为什么选跳表



中：知道Zset用跳表，但说不出原因



差：不知道Zset底层实现



参考答案：

（摘自Redis面试题文档-Zset实现原理、为什么用跳表而不是红黑树）



Zset底层实现：两种编码



Redis的Zset会根据元素个数和大小，动态选择两种编码方式：



1\. ziplist（压缩列表）



条件：元素个数 < 128 且 所有元素长度 < 64字节



结构：所有元素按score排序后连续存储，member和score交替排列



优点：内存紧凑，节省空间



缺点：插入删除要移动内存，复杂度O(N)



2\. skiplist（跳表）+ dict（哈希表）



条件：元素多或元素大时自动转换



结构：同时维护一个跳表（按score排序）和一个哈希表（按member查找）



优点：支持高效的范围查询和单点查询



c

// Redis源码中zset结构

typedef struct zset {

&nbsp;   dict \*dict;      // 成员 -> score 映射，用于ZSCORE O(1)

&nbsp;   zskiplist \*zsl;  // 跳表，用于范围查询

} zset;

跳表（Skip List）原理：



text

Level 4:  head -----------------------------------------------------> tail

Level 3:  head -------------------------------> 70 -----------------> tail

Level 2:  head -----------> 30 ---------------> 70 -----------> 90 -> tail

Level 1:  head -> 10 -----> 30 -----> 50 -----> 70 -----> 85 -> 90 -> tail

底层（Level 1）是原始有序链表



每向上层，元素按概率（通常1/2）出现，形成索引



查询时从高层向下层逐级搜索，时间复杂度O(log N)



为什么用跳表而不是红黑树？



维度	跳表	红黑树	B+树

实现复杂度	简单	复杂（旋转、变色）	复杂

范围查询	高效（链表遍历）	一般（需中序遍历）	高效（叶子链表）

内存占用	稍高（多层指针）	低	较高

并发修改	无锁容易实现	难	难

随机层高	概率均衡	严格平衡	严格平衡

Redis选跳表的核心理由：



实现简单，易于调试



跳表代码只有几百行，红黑树几千行且易出错



Redis追求稳定可靠，跳表bug概率低



范围查询友好



python

\# Zset常用操作：ZRANGE、ZRANGEBYSCORE都是范围查询

redis.zrange("ranking", 0, 9)  # 取Top10

redis.zrangebyscore("log", 1000, 2000)  # 取score在1000-2000之间的

跳表底层是链表，范围遍历直接指针移动



红黑树范围查询需要中序遍历，复杂



插入删除不需要复杂的平衡操作



跳表插入只需修改前后指针，随机层高



红黑树插入可能涉及多次旋转和变色



内存占用可接受



虽然比红黑树稍高，但Redis主要瓶颈不在索引内存



并发友好



跳表锁粒度可以更细，易于实现无锁



跳表核心代码结构：



c

// Redis跳表节点结构

typedef struct zskiplistNode {

&nbsp;   sds ele;                    // 成员字符串

&nbsp;   double score;                // 分值

&nbsp;   struct zskiplistNode \*backward; // 后退指针

&nbsp;   struct zskiplistLevel {

&nbsp;       struct zskiplistNode \*forward; // 前进指针

&nbsp;       unsigned long span;     // 跨度，用于计算排名

&nbsp;   } level\[];

} zskiplistNode;



typedef struct zskiplist {

&nbsp;   struct zskiplistNode \*header, \*tail;

&nbsp;   unsigned long length;       // 节点总数

&nbsp;   int level;                  // 当前最大层数

} zskiplist;

追问点：



跳表的层高怎么决定的？

\*（答：随机生成，每次有1/4的概率升层，最大32层）\*



时间复杂度是多少？

（答：平均O(log N)，最坏O(N)但概率极低）



问题5：Redis的过期删除策略有哪些？如果有大量key同时过期会有什么问题？

考察意图：



对Redis内存管理机制的理解



对过期策略的掌握



对过期可能导致的问题的预判能力



评分标准：



好：能说清两种策略（惰性删除+定期删除），解释定期删除的扫描机制，指出大量key同时过期可能导致的问题（Redis变慢、内存瞬间释放压力）



中：知道有策略，说不出细节



差：不知道过期怎么删除



参考答案：

（摘自Redis面试题文档-数据过期后的删除策略是什么？）



Redis过期删除策略：两种结合



1\. 惰性删除



原理：每次访问key时，检查是否过期，过期则删除



优点：CPU友好，只删除被访问的key



缺点：过期key如果不被访问，会一直占用内存



c

// Redis源码中惰性删除的伪代码

int expireIfNeeded(redisDb \*db, robj \*key) {

&nbsp;   if (!keyIsExpired(db, key)) return 0;

&nbsp;   

&nbsp;   // 删除过期key

&nbsp;   deleteKey(db,key);

&nbsp;   return 1;

}



// 每次操作前调用

if (expireIfNeeded(db,key)) {

&nbsp;   // key已过期，返回nil

&nbsp;   return NULL;

}

2\. 定期删除



原理：Redis每秒10次（默认）随机抽取一批设置了过期时间的key，检查并删除已过期的



频率：hz参数控制，默认10，即每秒10次



每次扫描：默认20个key（可配）



策略：如果超过25%的key过期，就继续扫描



c

// 定期删除的伪代码

void activeExpireCycle(void) {

&nbsp;   for (int i = 0; i < dbs\_per\_call; i++) {

&nbsp;       // 随机选20个有过期时间的key

&nbsp;       for (int j = 0; j < 20; j++) {

&nbsp;           if (key过期) {

&nbsp;               deleteKey(key);

&nbsp;           }

&nbsp;       }

&nbsp;       // 如果过期的比例超过25%，继续扫

&nbsp;   }

}

大量key同时过期的问题：



问题1：Redis变慢



定期删除时，如果大量key同时过期，Redis需要扫描更多key



CPU使用率瞬间升高，影响正常请求



问题2：内存瞬间释放压力



大量key过期时，内存会瞬间释放



但Redis是单线程，释放大内存可能阻塞



问题3：缓存雪崩



如果这些key是缓存数据，同时过期导致大量请求直接打到数据库



数据库可能被压垮



解决方案：



1\. 过期时间加随机值



python

\# 不推荐：所有缓存统一1小时过期

redis.setex("product:1001", 3600, data)



\# 推荐：基础时间 + 随机偏移

import random

expire\_time = 3600 + random.randint(0, 600)  # 1小时 + 0-10分钟随机

redis.setex(f"product:{product\_id}", expire\_time, data)

2\. 热点数据永不过期



python

\# 对于极其热点的数据，不设置过期时间

redis.set("hot\_product:1001", data)



\# 通过异步任务定期更新

@cron('\*/30 \* \* \* \*')  # 每30分钟

def refresh\_hot\_data():

&nbsp;   data = db.query("SELECT \* FROM hot\_products")

&nbsp;   redis.set("hot\_products", json.dumps(data))

3\. 错峰过期



python

\# 业务层面错峰

\# 用户A的数据在1点过期，用户B的数据在2点过期

expire\_time = 3600 \* (user\_id % 24)  # 分散到24小时

4\. 监控和告警



python

\# 监控过期key数量

info = redis.info('stats')

expired\_keys = info\['expired\_keys']



\# 如果短时间过期大量key，告警

if expired\_keys > threshold:

&nbsp;   alert("大量key过期，注意缓存雪崩风险")

追问点：



如果Redis内存满了怎么办？

（答：内存淘汰策略，如LRU/LFU）



淘汰策略有哪些？

（答：noeviction、allkeys-lru、volatile-lru、allkeys-lfu等）



第四部分：消息队列深度 (12分钟)

问题6：RabbitMQ如何保证消息的顺序性？如果顺序错了怎么处理？

考察意图：



对消息顺序性问题的理解



分布式系统下的顺序保障方案



异常处理能力



评分标准：



好：能说清RabbitMQ只能保证单队列有序，解释如何通过业务key路由到同一队列，消费端单线程处理，说明顺序错乱时的处理方案（状态机、版本号）



中：知道一个队列内有序，但说不出消费端怎么处理



差：说“RabbitMQ保证顺序”



参考答案：

（摘自RabbitMQ面试题文档-中的消息如何确保顺序性、消息队列面试题-如何保证消息的有序性）



RabbitMQ的顺序性保证：



RabbitMQ只能保证单队列内消息有序，不保证全局有序。



核心原则：



生产端：同一业务的消息必须发到同一个队列



消费端：一个队列只能有一个消费者（或单线程消费）



方案：按业务key路由到同一队列



python

\# 生产端：按订单ID取模，保证同一订单的消息到同一队列

def send\_order\_message(order\_id, event\_type, data):

&nbsp;   # 订单ID作为routing key，相同订单的消息到同一队列

&nbsp;   routing\_key = f"order.{order\_id % 10}"  # 10个队列

&nbsp;   

&nbsp;   channel.basic\_publish(

&nbsp;       exchange='order.exchange',

&nbsp;       routing\_key=routing\_key,

&nbsp;       body=json.dumps({

&nbsp;           "order\_id": order\_id,

&nbsp;           "event": event\_type,  # 如：created, paid, shipped

&nbsp;           "data": data,

&nbsp;           "sequence": get\_sequence(order\_id)  # 可选：序号

&nbsp;       }),

&nbsp;       properties=pika.BasicProperties(delivery\_mode=2)

&nbsp;   )



\# 消费端：每个队列单线程消费

def callback(ch, method, properties, body):

&nbsp;   message = json.loads(body)

&nbsp;   order\_id = message\['order\_id']

&nbsp;   

&nbsp;   # 单线程处理，保证同一订单消息有序

&nbsp;   process\_order\_message(order\_id, message)

&nbsp;   

&nbsp;   ch.basic\_ack(delivery\_tag=method.delivery\_tag)



\# 为每个队列启动一个消费者

for i in range(10):

&nbsp;   channel.basic\_consume(f"order.queue.{i}", callback)

为什么不能多消费者？



text

队列: \[消息1(订单A), 消息2(订单A), 消息3(订单A)]

&nbsp;         ↓          ↓          ↓

&nbsp;     消费者1     消费者2     消费者3

&nbsp;         ↓          ↓          ↓

&nbsp;     处理消息1   处理消息2   处理消息3  → 顺序错乱！

如果顺序错乱了怎么处理？



方案1：版本号/序号校验



python

def process\_order\_message(message):

&nbsp;   order\_id = message\['order\_id']

&nbsp;   sequence = message\['sequence']

&nbsp;   

&nbsp;   # 从Redis获取已处理的最后序号

&nbsp;   last\_sequence = redis.get(f"order\_seq:{order\_id}") or 0

&nbsp;   

&nbsp;   if sequence <= last\_sequence:

&nbsp;       # 重复或乱序，直接丢弃

&nbsp;       logger.warning(f"收到乱序或重复消息: order={order\_id}, seq={sequence}, last={last\_sequence}")

&nbsp;       return

&nbsp;   

&nbsp;   # 处理消息

&nbsp;   update\_order\_status(order\_id, message\['event'], message\['data'])

&nbsp;   

&nbsp;   # 更新最后序号

&nbsp;   redis.set(f"order\_seq:{order\_id}", sequence)

方案2：状态机幂等



python

\# 订单状态机：待支付 → 已支付 → 已发货 → 已完成 → 已取消

\# 状态只能按顺序流转



def handle\_order\_event(order\_id, event):

&nbsp;   # 获取当前订单状态

&nbsp;   status = db.get("SELECT status FROM orders WHERE id=%s", order\_id)

&nbsp;   

&nbsp;   # 状态流转表

&nbsp;   allowed\_transitions = {

&nbsp;       'pending': \['paid', 'cancelled'],

&nbsp;       'paid': \['shipped', 'cancelled'],

&nbsp;       'shipped': \['completed'],

&nbsp;       'completed': \[],

&nbsp;       'cancelled': \[]

&nbsp;   }

&nbsp;   

&nbsp;   # 如果当前状态不允许该事件，说明顺序错了

&nbsp;   if event not in allowed\_transitions\[status]:

&nbsp;       logger.error(f"订单状态错误: order={order\_id}, status={status}, event={event}")

&nbsp;       # 可能放入死信队列等待人工处理

&nbsp;       return False

&nbsp;   

&nbsp;   # 执行状态变更

&nbsp;   db.execute("UPDATE orders SET status=%s WHERE id=%s", event, order\_id)

&nbsp;   return True

方案3：延迟队列+重排



python

\# 如果怀疑消息乱序，先不处理，等一会儿

def safe\_process(message):

&nbsp;   # 延迟10秒处理，等前面的消息先到

&nbsp;   time.sleep(10)

&nbsp;   process\_message(message)



\# 或用死信队列实现延迟

方案4：最终一致性兜底



python

\# 即使处理错了，定时任务修复

@cron('0 2 \* \* \*')  # 每天凌晨2点

def repair\_order\_state():

&nbsp;   # 扫描异常的订单（如支付后未发货超过24小时）

&nbsp;   abnormal\_orders = db.query("""

&nbsp;       SELECT \* FROM orders 

&nbsp;       WHERE status='paid' AND update\_time < NOW() - INTERVAL 24 HOUR

&nbsp;   """)

&nbsp;   

&nbsp;   for order in abnormal\_orders:

&nbsp;       # 检查MQ中是否有未处理的消息

&nbsp;       # 或直接人工介入

&nbsp;       repair\_order(order\['id'])

最佳实践：



按业务key路由到同一队列



单队列单线程消费



消费者做幂等和状态校验



定时任务补偿



追问点：



如果必须用多个消费者提高吞吐，怎么保证顺序？

（答：按业务key分区，每个分区一个消费者）



Kafka怎么保证顺序？

（答：分区内有序，和RabbitMQ类似）



问题7：如果消息队列突然积压了几十万条消息，你怎么处理？

考察意图：



线上故障处理能力



应急响应思路



对MQ特性的掌握



评分标准：



好：能分步骤说明（先定位原因，再临时扩容，再优化代码，再考虑降级），能具体说明扩容方案（增加消费者、增加分区、临时队列），能说明如何避免再次发生



中：只说“加消费者”，说不出具体操作



差：不知道怎么办



参考答案：

（摘自消息队列面试题文档-如何处理消息堆积）



消息积压排查处理流程：



第一步：定位原因



python

\# 1. 查看监控：消费者的lag（堆积量）是否持续增长

\# 2. 查看消费者日志：是否有异常、处理时间是否变长

\# 3. 查看下游（数据库、API）是否变慢



\# RabbitMQ查看队列积压

rabbitmqctl list\_queues name messages consumers



\# Kafka查看消费者滞后

kafka-consumer-groups --bootstrap-server localhost:9092 \\

&nbsp; --group order-group --describe

常见原因：



消费者处理慢（代码性能问题）



下游数据库变慢，导致事务阻塞



消费者实例挂了，没人消费



消息突然暴增（大促、刷单）



第二步：紧急处理 - 临时扩容



方案1：增加消费者实例



python

\# RabbitMQ：增加同队列的消费者（注意：同一队列的消息会被平分）

\# 但RabbitMQ默认轮询分发，增加消费者能提升处理速度



\# Kafka：增加消费者实例，但要确保分区数 >= 消费者数

\# 如果分区数不够，需要增加分区

kafka-topics --alter --topic order-topic --partitions 20

方案2：临时增加分区（Kafka）



bash

\# 原分区数10，不够，临时增加到20

kafka-topics --bootstrap-server localhost:9092 \\

&nbsp; --alter --topic order-topic --partitions 20



\# 注意：增加分区可能导致key到分区的映射变化，但能提升并行度

方案3：启动紧急消费者脚本



python

\# 写一个临时消费者，只做简单处理，快速消费

\# 例如：先只记录日志，不处理业务

def emergency\_consume():

&nbsp;   for msg in consumer:

&nbsp;       # 只保存到临时表或文件，快速ACK

&nbsp;       save\_to\_temp(msg.value())

&nbsp;       consumer.commit()  # 立即提交，快速消费

&nbsp;   # 等积压缓解后，再处理这些临时数据

第三步：优化消费速度



优化1：批量处理



python

\# 原来：一次处理一条

def handle\_message(msg):

&nbsp;   process\_one(msg)

&nbsp;   consumer.commit()



\# 优化后：攒一批处理

messages = \[]

for msg in consumer.poll(timeout=1000):

&nbsp;   messages.append(msg)

&nbsp;   if len(messages) >= 100:

&nbsp;       # 批量处理

&nbsp;       batch\_process(messages)

&nbsp;       consumer.commit()

&nbsp;       messages = \[]

优化2：异步化 + 线程池



python

from concurrent.futures import ThreadPoolExecutor



executor = ThreadPoolExecutor(max\_workers=20)



def handle\_message(msg):

&nbsp;   # 提交到线程池异步处理，主线程快速ACK

&nbsp;   executor.submit(process\_one, msg)

&nbsp;   consumer.commit()  # 立即提交，但注意：如果process失败，消息已提交无法重试

&nbsp;   # 改进：使用手动ACK，处理成功才提交

优化3：精简处理逻辑



python

\# 原逻辑：处理中调用多个外部服务

def process\_one(msg):

&nbsp;   call\_service\_a()  # 慢

&nbsp;   call\_service\_b()  # 慢

&nbsp;   update\_db()        # 可能死锁



\# 优化：非核心逻辑异步化，只保留核心

def process\_one(msg):

&nbsp;   update\_db\_core()  # 只做必须的DB更新

&nbsp;   # 其他逻辑发到另一个队列异步处理

&nbsp;   mq.send("secondary\_queue", msg)

第四步：降级策略



如果业务允许，可以临时降级：



python

def process\_with\_degrade(msg):

&nbsp;   if is\_critical\_business(msg):

&nbsp;       # 核心业务，必须处理

&nbsp;       process\_critical(msg)

&nbsp;   else:

&nbsp;       # 非核心业务（如日志、统计），暂时丢弃或记录

&nbsp;       log\_to\_file(msg)

&nbsp;       # 等恢复正常后再补处理

第五步：避免再次发生



监控告警：设置lag阈值，超过就告警



python

\# Prometheus监控

consumer\_lag > 10000  # 告警

自动扩容：根据lag自动增加消费者



python

if lag > threshold:

&nbsp;   k8s.scale\_deployment("order-consumer", replicas=current\*2)

压测优化：定期压测，找出消费瓶颈



限流保护：生产端限流，避免瞬间暴增



追问点：



如果增加消费者后还是处理不过来？

（答：检查下游瓶颈，可能需要分库分表）



消息积压后，怎么保证不丢失？

（答：MQ有持久化，只要不删除，消息还在）



如果消息已经过期了怎么办？

（答：根据业务决定是否重发或补偿）



第五部分：数据库深度 (12分钟)

问题8：MySQL的主从同步延迟怎么产生的？怎么解决？

考察意图：



对MySQL复制原理的理解



主从延迟排查能力



高可用架构设计



评分标准：



好：能说清主从延迟的原因（大事务、从库单线程、网络延迟），给出解决方案（并行复制、分库分表、强制读主库），能结合业务场景说明



中：知道有延迟，说不出原因和解决方案



差：不知道主从延迟



参考答案：

（摘自MySQL面试题文档-如何处理主从同步延迟）



主从同步原理回顾：



text

主库执行事务 → 写入binlog → 从库IO线程拉取binlog → 写入relay log → SQL线程重放

主从延迟的常见原因：



原因1：大事务



sql

-- 一次性删100万行

DELETE FROM orders WHERE create\_time < '2020-01-01';

-- 主库很快执行完，但从库要一条条执行，延迟巨大

原因2：从库单线程回放



主库可以并发写，但从库SQL线程是单线程



主库压力大时，从库跟不上



原因3：从库资源不足



从库CPU、IOPS比主库差



从库同时对外提供读服务，影响同步速度



原因4：网络延迟



主从跨机房，网络延迟高



原因5：表上无主键



从库回放时，UPDATE/DELETE需要全表扫描找数据，极慢



解决方案：



方案1：优化大事务



python

\# 不推荐：一次删太多

db.execute("DELETE FROM logs WHERE create\_time < '2023-01-01'")



\# 推荐：分批删除，每次1000条

def batch\_delete():

&nbsp;   while True:

&nbsp;       affected = db.execute("""

&nbsp;           DELETE FROM logs 

&nbsp;           WHERE create\_time < '2023-01-01' 

&nbsp;           LIMIT 1000

&nbsp;       """)

&nbsp;       if affected == 0:

&nbsp;           break

&nbsp;       time.sleep(1)  # 暂停1秒，给从库喘息时间

方案2：启用并行复制（MySQL 5.7+）



sql

-- 设置并行复制

-- 基于数据库的并行复制（不同库可以并行）

SET GLOBAL slave\_parallel\_type = 'DATABASE';

SET GLOBAL slave\_parallel\_workers = 4;



-- MySQL 5.7+ 支持基于组提交的并行复制（同一库也能并行）

SET GLOBAL slave\_parallel\_type = 'LOGICAL\_CLOCK';

SET GLOBAL slave\_parallel\_workers = 4;

方案3：读写分离策略



python

\# 读核心业务（实时性要求高）走主库

def get\_order\_detail(order\_id):

&nbsp;   if is\_critical\_order(order\_id):

&nbsp;       # 走主库

&nbsp;       return db\_master.query("SELECT \* FROM orders WHERE id=%s", order\_id)

&nbsp;   else:

&nbsp;       # 走从库

&nbsp;       return db\_slave.query("SELECT \* FROM orders WHERE id=%s", order\_id)



\# 写后立即读的场景，强制走主库

def create\_order(order\_data):

&nbsp;   # 写主库

&nbsp;   db\_master.execute("INSERT INTO orders ...", order\_data)

&nbsp;   

&nbsp;   # 立即读刚创建的订单，强制走主库

&nbsp;   return db\_master.query("SELECT \* FROM orders WHERE id=LAST\_INSERT\_ID()")

方案4：从库加索引



sql

-- 从库可以加不同于主库的索引

-- 主库索引用于写入，从库索引用于查询

ALTER TABLE orders SLAVE ADD INDEX idx\_user (user\_id);

方案5：监控和告警



sql

-- 查看从库延迟

SHOW SLAVE STATUS\\G

-- 关键字段：

-- Seconds\_Behind\_Master: 延迟秒数

-- Slave\_IO\_Running: IO线程状态

-- Slave\_SQL\_Running: SQL线程状态



-- 如果延迟超过阈值，告警

if seconds\_behind\_master > 60:

&nbsp;   alert("主从延迟超过60秒")

方案6：分库分表



单库压力太大，拆成多个库，每个库的从库压力也减小



方案7：使用更高性能的硬件



从库用SSD，提升IO性能



业务层面的妥协：



场景：用户下单后立即查订单



python

def create\_and\_get\_order():

&nbsp;   # 创建订单

&nbsp;   order\_id = create\_order()

&nbsp;   

&nbsp;   # 不直接查，而是等1秒，或轮询

&nbsp;   time.sleep(1)

&nbsp;   

&nbsp;   # 或者先查缓存

&nbsp;   return cache.get(f"order:{order\_id}") or db.query(...)

场景：报表查询



python

\# 报表查询可以接受分钟级延迟，直接读从库

def generate\_report():

&nbsp;   data = db\_slave.query("SELECT ... FROM big\_table")

&nbsp;   return data

追问点：



主从延迟怎么监控？

（答：Seconds\_Behind\_Master，但要注意这个值可能不准）



如果主库挂了，怎么切换？

（答：手动或使用MHA、Orchestrator等工具）



问题9：MySQL的死锁是怎么产生的？你怎么排查和解决？

考察意图：



对锁机制的理解



死锁排查能力



问题解决经验



评分标准：



好：能说清死锁产生的四个必要条件，举例说明并发更新导致的死锁，说明如何查看死锁日志，给出解决方案（调整事务顺序、降低隔离级别、缩短事务）



中：知道死锁概念，说不出具体排查方法



差：不知道死锁



参考答案：

（摘自MySQL面试题文档-中如果发生死锁应该如何解决）



什么是死锁？



两个或多个事务互相持有对方需要的锁，谁也无法继续执行。



死锁产生的四个必要条件：



互斥：资源不能被共享（行锁）



持有并等待：事务持有锁，并等待其他锁



不可剥夺：已获得的锁不能被强制剥夺



循环等待：形成等待环路



典型死锁场景：



场景1：两个事务更新相反顺序



sql

-- 事务A

BEGIN;

UPDATE orders SET status='paid' WHERE id=1;  -- 锁住id=1

-- 此时事务B执行了下面的第1步

UPDATE orders SET status='shipped' WHERE id=2;  -- 等待事务B释放id=2

-- 死锁发生！



-- 事务B

BEGIN;

UPDATE orders SET status='shipped' WHERE id=2;  -- 锁住id=2

UPDATE orders SET status='paid' WHERE id=1;  -- 等待事务A释放id=1

-- 死锁发生！

场景2：间隙锁导致的死锁



sql

-- 事务A

BEGIN;

SELECT \* FROM orders WHERE amount > 100 FOR UPDATE;  -- 间隙锁 (100, +∞)

-- 事务B

BEGIN;

SELECT \* FROM orders WHERE amount > 200 FOR UPDATE;  -- 也想锁(200,+∞)，但被A锁了一部分

INSERT INTO orders (amount) VALUES (150);  -- 等待间隙锁

-- 死锁

如何排查死锁？



1\. 查看最近一次死锁日志



sql

SHOW ENGINE INNODB STATUS\\G

-- 找到 "LATEST DETECTED DEADLOCK" 部分

死锁日志示例：



text

------------------------

LATEST DETECTED DEADLOCK

------------------------

\*\*\* (1) TRANSACTION:

TRANSACTION 3100, ACTIVE 12 sec

mysql tables in use 1, locked 1

LOCK WAIT 3 lock struct(s)

UPDATE orders SET status='shipped' WHERE id=2



\*\*\* (1) HOLDS THE LOCK(S):

记录id=2的行锁



\*\*\* (1) WAITING FOR THIS LOCK TO BE GRANTED:

等待id=1的行锁



\*\*\* (2) TRANSACTION:

TRANSACTION 3101, ACTIVE 8 sec

UPDATE orders SET status='paid' WHERE id=1



\*\*\* (2) HOLDS THE LOCK(S):

记录id=1的行锁



\*\*\* (2) WAITING FOR THIS LOCK TO BE GRANTED:

等待id=2的行锁



\*\*\* WE ROLL BACK TRANSACTION (2)

2\. 分析死锁原因



看两个事务分别持有什么锁



看它们在等什么锁



看SQL执行顺序



解决方案：



方案1：保证加锁顺序一致



python

\# 错误：可能按不同顺序更新

def update\_order(order\_id):

&nbsp;   db.execute("UPDATE orders SET status='paid' WHERE id=%s", order\_id)



\# 两个并发调用，可能一个传1一个传2，导致死锁



\# 正确：强制按id排序

def update\_orders\_safe(order\_ids):

&nbsp;   # 先排序，保证所有事务按相同顺序加锁

&nbsp;   order\_ids.sort()

&nbsp;   for order\_id in order\_ids:

&nbsp;       db.execute("UPDATE orders SET status='paid' WHERE id=%s", order\_id)

方案2：缩短事务时间



python

\# 错误：事务里做太多事

@transactional

def process\_order(order\_id):

&nbsp;   order = db.query("SELECT \* FROM orders WHERE id=%s", order\_id)

&nbsp;   call\_external\_api(order)  # 慢，事务一直持有锁

&nbsp;   db.execute("UPDATE orders SET status='processed' WHERE id=%s", order\_id)



\# 正确：事务外做耗时操作

def process\_order\_safe(order\_id):

&nbsp;   order = db.query("SELECT \* FROM orders WHERE id=%s", order\_id)

&nbsp;   call\_external\_api(order)  # 不在事务内

&nbsp;   with transaction():

&nbsp;       db.execute("UPDATE orders SET status='processed' WHERE id=%s", order\_id)

方案3：降低隔离级别



sql

-- 如果业务允许，从RR降到RC，减少间隙锁

SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

方案4：使用乐观锁



python

def update\_with\_optimistic\_lock(order\_id, expected\_version):

&nbsp;   # 使用版本号，不加锁

&nbsp;   affected = db.execute("""

&nbsp;       UPDATE orders 

&nbsp;       SET status='paid', version=version+1 

&nbsp;       WHERE id=%s AND version=%s

&nbsp;   """, order\_id, expected\_version)

&nbsp;   

&nbsp;   if affected == 0:

&nbsp;       # 数据被修改过，重试

&nbsp;       return retry\_update(order\_id)

&nbsp;   return True

方案5：索引优化



确保UPDATE和DELETE语句能用上索引，减少锁范围



方案6：重试机制（最后防线）



python

from retry import retry



@retry(MySQLdb.DeadlockError, tries=3, delay=1)

def update\_order\_with\_retry(order\_id):

&nbsp;   db.execute("UPDATE orders SET status='paid' WHERE id=%s", order\_id)

&nbsp;   db.commit()

死锁发生后，MySQL怎么处理？



MySQL会自动检测死锁，回滚其中一个事务（通常是影响行数少的）



应用程序需要捕获死锁异常并重试



追问点：



怎么避免间隙锁？

（答：降低隔离级别到RC，或确保查询条件能唯一锁定行）



死锁和锁等待的区别？

（答：死锁是循环等待，锁等待是一个等另一个，最终会超时）



第六部分：沟通能力与场景题 (8分钟)

问题10：测试反馈你的接口返回数据和预期不一致，你怎么沟通和处理？

考察意图：



问题排查思路



跨团队沟通能力



责任心和态度



评分标准：



好：先承认问题，感谢反馈，主动排查差异，对齐数据口径，如果真的是bug就承认并修复，如果是口径问题就耐心解释并更新文档



中：直接说是测试环境问题或测试测错了



差：甩锅，不排查



参考答案：

\*（摘自Python沟通能力考察文档-问题3）\*



完整处理流程：



第一步：收到反馈，先不反驳



text

测试同学：你这个接口返回的数据不对，和我自己查数据库的不一样！



我：收到反馈，感谢你发现问题。我们一起排查一下，先把问题定位清楚。

第二步：主动排查，对比差异



python

\# 1. 确认测试环境和数据

def debug\_api():

&nbsp;   # 调用接口

&nbsp;   api\_data = call\_api(order\_id=123)

&nbsp;   

&nbsp;   # 直接查数据库

&nbsp;   db\_data = db.query("SELECT \* FROM orders WHERE id=123")

&nbsp;   

&nbsp;   # 对比差异

&nbsp;   print("API返回:", api\_data)

&nbsp;   print("数据库:", db\_data)

&nbsp;   

&nbsp;   # 打印SQL日志

&nbsp;   print("执行的SQL:", get\_last\_sql())

第三步：定位原因（常见差异点）



情况1：数据口径不一致



text

可能原因：

\- 接口默认只返回最近30天数据，测试查的是所有数据

\- 接口返回的是已支付订单，测试查了所有状态

\- 接口做了脱敏（如手机号中间4位\*\*\*），测试看到的是原始数据

情况2：缓存未更新



python

\# 接口先查缓存，缓存还是旧数据

def get\_order(order\_id):

&nbsp;   # 先查缓存

&nbsp;   cached = redis.get(f"order:{order\_id}")

&nbsp;   if cached:

&nbsp;       return cached  # 缓存还是旧数据！

&nbsp;   

&nbsp;   # 再查数据库

&nbsp;   data = db.query(...)

&nbsp;   redis.setex(f"order:{order\_id}", 3600, data)

&nbsp;   return data

情况3：SQL真的写错了



python

\# 比如漏了过滤条件

def get\_paid\_orders():

&nbsp;   # 错误：没加WHERE status='paid'

&nbsp;   return db.query("SELECT \* FROM orders")

第四步：沟通结果



如果是口径问题：



text

我：我查了一下，原因是这样的：

\- 接口默认只返回最近30天的订单（产品需求如此）

\- 你查的是全部数据，所以多了3条



建议方案：

1\. 如果确实需要全部数据，我可以在接口加个参数`all=true`

2\. 或者你用另一个内部接口，不限制时间



我更新一下文档，把默认行为写清楚，避免大家误解。

如果是bug：



text

我：确实是我的bug，SQL漏了status过滤条件，导致返回了所有订单。



非常抱歉，我马上修复：

1\. 加回`WHERE status='paid'`

2\. 加单元测试覆盖这种情况

3\. 10分钟内上线修复



谢谢你发现这个问题，不然上线就出事故了。

如果是缓存问题：



text

我：原因是缓存没有及时更新。修改订单后，我删了缓存但另一个线程又把旧数据写回去了。



修复方案：

1\. 先更新数据库，再删缓存（保证最终一致）

2\. 缓存时间设置短一点（5分钟）

3\. 加监控，如果缓存删除失败，发消息重试



我改好后，你再帮忙验证一下。

第五步：后续改进



更新接口文档，明确字段含义和默认行为



加单元测试，覆盖边界情况



和测试对齐数据口径，建立统一的测试数据



沟通要点：



不甩锅：不说是测试环境问题、不说是测试测错了



不指责：不说“你查错了”



给方案：不管是bug还是口径问题，都给出解决方案



感谢反馈：测试帮你发现问题，要感谢



追问点：



如果测试坚持说是你错，你觉得自己没错，怎么办？

（答：拉产品一起确认需求，以文档为准）



如果修复后测试还说没修好？

（答：一起复现，可能是环境没更新或回归问题）



第七部分：架构设计 (8分钟)

问题11：如果让你设计一个短链系统，你会怎么设计？

考察意图：



系统设计能力



存储选型



唯一ID生成



重定向原理



评分标准：



好：能说清短链生成的算法（发号器、哈希、Base62），存储选型（Redis+MySQL），重定向流程（301/302），能处理高并发和防攻击



中：能说一部分，但不完整



差：不知道短链怎么生成



参考答案：

（摘自后端场景面试题文档-让你设计一个短链系统）



短链系统核心功能：



长链 → 短链（生成）



短链 → 长链（跳转）



整体架构：



text

用户访问短链 → DNS → Nginx/LVS → 应用集群 → \[缓存Redis] → \[数据库MySQL]

&nbsp;                             ↓

&nbsp;                        生成短链服务

核心模块设计：



1\. 短链生成算法



方案1：发号器 + Base62（推荐）



python

\# 使用分布式ID发号器（Snowflake、数据库号段）生成唯一ID

\# 将ID转为62进制字符串（a-z A-Z 0-9），长度6-8位



def id\_to\_short\_code(id):

&nbsp;   chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'

&nbsp;   base = len(chars)

&nbsp;   result = \[]

&nbsp;   

&nbsp;   while id > 0:

&nbsp;       result.append(chars\[id % base])

&nbsp;       id //= base

&nbsp;   

&nbsp;   # 反转并补全到6位

&nbsp;   short\_code = ''.join(reversed(result)).rjust(6, '0')

&nbsp;   return short\_code



\# 示例

short\_code = id\_to\_short\_code(123456)  # "w7e"

方案2：哈希 + 去重



python

import hashlib



def long\_to\_short(long\_url):

&nbsp;   # MD5哈希

&nbsp;   md5 = hashlib.md5(long\_url.encode()).hexdigest()

&nbsp;   # 取前6位

&nbsp;   short\_code = md5\[:6]

&nbsp;   

&nbsp;   # 检查冲突

&nbsp;   if redis.exists(f"short:{short\_code}"):

&nbsp;       # 冲突了，加盐再哈希

&nbsp;       short\_code = md5\[6:12]

&nbsp;   

&nbsp;   return short\_code

2\. 存储设计



Redis（热数据）：



python

\# key: short:{short\_code}

\# value: 长链URL

\# TTL: 7天（热点数据）



\# 跳转时先查Redis

def redirect(short\_code):

&nbsp;   long\_url = redis.get(f"short:{short\_code}")

&nbsp;   if long\_url:

&nbsp;       return long\_url

&nbsp;   

&nbsp;   # 查MySQL

&nbsp;   long\_url = db.query("SELECT long\_url FROM short\_urls WHERE short\_code=%s", short\_code)

&nbsp;   if long\_url:

&nbsp;       redis.setex(f"short:{short\_code}", 86400\*7, long\_url)  # 缓存7天

&nbsp;   

&nbsp;   return long\_url

MySQL（全量数据）：



sql

CREATE TABLE short\_urls (

&nbsp;   id BIGINT PRIMARY KEY AUTO\_INCREMENT,

&nbsp;   short\_code VARCHAR(16) NOT NULL UNIQUE,  -- 短码，唯一索引

&nbsp;   long\_url TEXT NOT NULL,                  -- 原长链

&nbsp;   user\_id INT DEFAULT NULL,                 -- 创建用户（可选）

&nbsp;   expire\_time DATETIME DEFAULT NULL,         -- 过期时间（可选）

&nbsp;   create\_time DATETIME DEFAULT CURRENT\_TIMESTAMP,

&nbsp;   access\_count INT DEFAULT 0,                -- 访问次数

&nbsp;   INDEX idx\_expire (expire\_time)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

3\. 跳转逻辑



python

from flask import redirect, abort



@app.route('/<short\_code>')

def redirect\_to\_long(short\_code):

&nbsp;   # 1. 校验短码格式

&nbsp;   if not is\_valid\_short\_code(short\_code):

&nbsp;       abort(404)

&nbsp;   

&nbsp;   # 2. 查Redis

&nbsp;   long\_url = redis.get(f"short:{short\_code}")

&nbsp;   

&nbsp;   # 3. 查MySQL

&nbsp;   if not long\_url:

&nbsp;       result = db.query("SELECT long\_url, expire\_time FROM short\_urls WHERE short\_code=%s", short\_code)

&nbsp;       if not result:

&nbsp;           abort(404)

&nbsp;       

&nbsp;       long\_url = result\['long\_url']

&nbsp;       expire\_time = result\['expire\_time']

&nbsp;       

&nbsp;       # 检查是否过期

&nbsp;       if expire\_time and expire\_time < datetime.now():

&nbsp;           abort(410)  # Gone

&nbsp;       

&nbsp;       # 写Redis

&nbsp;       redis.setex(f"short:{short\_code}", 86400\*7, long\_url)

&nbsp;       

&nbsp;       # 异步增加访问计数

&nbsp;       db.execute("UPDATE short\_urls SET access\_count=access\_count+1 WHERE short\_code=%s", short\_code)

&nbsp;   

&nbsp;   # 4. 重定向

&nbsp;   return redirect(long\_url, code=302)  # 302临时重定向，方便统计

301 vs 302：



301永久重定向：浏览器会缓存，下次直接访问长链，无法统计点击量



302临时重定向：每次都会经过短链服务，可以统计点击



4\. 高并发优化



python

\# 1. 预热热点短链到Redis

def warm\_up\_hot\_short\_urls():

&nbsp;   hot\_urls = db.query("SELECT short\_code, long\_url FROM short\_urls WHERE access\_count > 1000")

&nbsp;   for url in hot\_urls:

&nbsp;       redis.setex(f"short:{url\['short\_code']}", 86400\*7, url\['long\_url'])



\# 2. 布隆过滤器拦截无效短链

def init\_bloom\_filter():

&nbsp;   bf = BloomFilter(redis\_key="short\_urls\_filter", capacity=1000000)

&nbsp;   all\_codes = db.query("SELECT short\_code FROM short\_urls")

&nbsp;   for code in all\_codes:

&nbsp;       bf.add(code)



@app.route('/<short\_code>')

def redirect\_with\_bloom(short\_code):

&nbsp;   if not bf.exists(short\_code):

&nbsp;       abort(404)  # 肯定不存在，直接返回

&nbsp;   # 继续正常流程

5\. 防攻击设计



python

\# 1. 限流

def rate\_limit(ip):

&nbsp;   key = f"rate:short:{ip}"

&nbsp;   count = redis.incr(key)

&nbsp;   if count == 1:

&nbsp;       redis.expire(key, 60)  # 60秒

&nbsp;   return count <= 10  # 每分钟最多10次



\# 2. 黑名单

def is\_blocked(ip):

&nbsp;   return redis.sismember("blacklist:short", ip)



\# 3. 验证码（可疑操作）

@app.route('/create')

def create\_short\_url():

&nbsp;   if is\_suspicious(request):

&nbsp;       if not verify\_captcha(request):

&nbsp;           return {"code": 400, "message": "需要验证码"}

6\. 扩展功能



python

\# 1. 自定义短链

@app.route('/custom')

def create\_custom():

&nbsp;   custom\_code = request.args.get('code')

&nbsp;   if redis.exists(f"short:{custom\_code}"):

&nbsp;       return {"code": 400, "message": "短链已存在"}

&nbsp;   

&nbsp;   save\_short\_url(custom\_code, long\_url)

&nbsp;   return {"short\_url": f"https://s.example/{custom\_code}"}



\# 2. 统计报表

@app.route('/stats/<short\_code>')

def get\_stats(short\_code):

&nbsp;   data = db.query("""

&nbsp;       SELECT access\_count, create\_time 

&nbsp;       FROM short\_urls 

&nbsp;       WHERE short\_code=%s

&nbsp;   """, short\_code)

&nbsp;   return data

追问点：



短链重复了怎么办？

（答：发号器保证唯一，哈希冲突则加盐重试）



短链过期了怎么办？

（答：数据库标记过期，Redis不缓存，跳转时返回410）



第八部分：反问环节 (3分钟)

问题12：你有什么想问我的吗？

考察意图：



对团队和业务的兴趣



职业规划



沟通主动性



评分标准：



好：问出有深度、有针对性的问题（基于前面面试的交流）



中：问常规问题



差：没问题



推荐问题（二面专属）：



技术深度相关：



咱们团队在数据库这块遇到过最大的挑战是什么？怎么解决的？



咱们有遇到过缓存雪崩或穿透的问题吗？当时是怎么应对的？



咱们在消息队列选型上，为什么选RabbitMQ/Kafka？有考虑过Pulsar吗？



架构演进：



咱们当前系统的瓶颈主要在哪个层面？未来有什么架构演进计划？



咱们有没有开始往云原生方向演进？比如K8s、Service Mesh？



咱们在微服务拆分上做到什么粒度了？服务间调用怎么治理的？



团队与技术氛围：



咱们团队的技术分享氛围怎么样？有固定的Code Review机制吗？



如果我有新的技术想法，团队支持尝试吗？有技术创新的空间吗？



咱们团队目前最缺什么样的人才？希望我加入后能解决什么问题？



业务挑战：



咱们业务目前最大的技术挑战是什么？



接下来半年最重要的技术项目是什么？



不推荐的问题：



加班多吗？（二面不问这个）



什么时候能入职？（还早）



工资多少？（HR面再问）



面试评分总表

考察维度	权重	评分项	得分(1-10)	备注

架构设计能力	20%	秒杀系统设计		

短链系统设计		

高并发处理	20%	缓存应用深度		

消息队列应用		

数据库深度	20%	主从延迟处理		

死锁排查解决		

过期/淘汰策略		

消息队列	15%	顺序性保障		

积压处理		

源码理解	10%	Zset实现原理		

沟通能力	10%	测试问题沟通		

反问环节	5%	问题质量		

总分	100%			

评级标准：



90-100分：卓越，架构师潜质，可考虑P8级别



80-89分：优秀，技术深度足够，可独立负责核心模块



70-79分：良好，基础扎实，有培养潜力



60-69分：及格，需要加强系统设计能力



60分以下：待定，建议加面或挂



以上是完整的Python工程师技术二面详细文档，包含：



12个核心问题（每题都有考察意图、评分标准、详细参考答案）



涵盖高并发设计、架构设计、数据库深度、消息队列深度、沟通场景



每题包含追问点和文档出处



完整的面试流程和时间分配



面试评分表

