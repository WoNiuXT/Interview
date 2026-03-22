Python工程师技术三面 - 详细面试文档

基本信息

面试岗位：Python开发工程师/技术专家



面试轮次：三面（技术终面/交叉面）



面试时长：75-90分钟



目标公司：对标腾讯T5/T6、阿里P8/P9、字节3-1/3-2级别



考察重点：系统架构能力、技术视野、跨团队协作、复杂业务建模、技术前瞻性、领导力、疑难杂症排查



第一部分：复杂业务建模与架构设计 (20分钟)

问题1：请设计一个电商系统的订单中心，要求支持高并发、可扩展、数据最终一致。请从整体架构、模块划分、数据流向、技术选型等方面详细说明

考察意图：



复杂业务建模能力



架构设计思维



技术选型权衡



对分布式系统的理解



评分标准：



好 (8-10分)：能画出完整架构图，说清各模块职责，数据流向清晰，能说明为什么这么选型，能预判未来扩展点，能处理边界情况



中 (5-7分)：能说清主要模块，但缺乏整体视角，选型理由不充分



差 (0-4分)：逻辑混乱，只说CRUD



参考答案：



订单中心核心挑战：



高并发写入（大促峰值10万+ QPS）



数据一致性（库存扣减、状态流转）



可扩展性（业务快速发展）



高可用（99.99%）



整体架构图：



text

【接入层】

客户端 → CDN → 四层LB( LVS/F5 ) → 七层LB( Nginx/OpenResty )

&nbsp;                ↓

【网关层】Spring Cloud Gateway / Kong

&nbsp;   → 路由、鉴权、限流、熔断、降级、日志

&nbsp;                ↓

【业务层】订单服务集群（无状态，水平扩展）

&nbsp;   ├─ 订单创建服务

&nbsp;   ├─ 订单查询服务

&nbsp;   ├─ 订单状态机

&nbsp;   ├─ 超时处理服务

&nbsp;   └─ 订单同步服务

&nbsp;                ↓

【中间件层】

&nbsp;   ├─ 缓存：Redis Cluster（热点数据、分布式锁）

&nbsp;   ├─ 消息队列：RocketMQ/RabbitMQ（异步解耦、削峰填谷）

&nbsp;   └─ 搜索引擎：Elasticsearch（订单搜索）

&nbsp;                ↓

【存储层】

&nbsp;   ├─ 主库：MySQL分库分表（ShardingSphere/MyCat）

&nbsp;   ├─ 从库：MySQL只读实例（读写分离）

&nbsp;   ├─ 历史库：TiDB/HBase（冷数据）

&nbsp;   └─ 数仓：Hive/ClickHouse（数据分析）

&nbsp;                ↓

【监控/运维层】

&nbsp;   ├─ 监控：Prometheus + Grafana

&nbsp;   ├─ 日志：ELK/EFK

&nbsp;   ├─ 链路追踪：SkyWalking/Pinpoint

&nbsp;   └─ 告警：AlertManager

模块详细设计：



1\. 订单创建服务



python

\# 核心流程：幂等校验 → 库存预占 → 生成订单 → 发送消息 → 返回结果



@transactional

def create\_order(request):

&nbsp;   # 1. 幂等校验（防止重复提交）

&nbsp;   if check\_idempotent(request.idempotent\_token):

&nbsp;       return get\_existing\_order(request.idempotent\_token)

&nbsp;   

&nbsp;   # 2. 库存预占（Redis原子操作）

&nbsp;   stock\_result = redis.eval(STOCK\_LUA, 1, f"stock:{request.product\_id}", request.quantity)

&nbsp;   if stock\_result <= 0:

&nbsp;       raise BizException("库存不足")

&nbsp;   

&nbsp;   # 3. 生成订单

&nbsp;   order = Order(

&nbsp;       order\_id = generate\_order\_id(),

&nbsp;       user\_id = request.user\_id,

&nbsp;       product\_id = request.product\_id,

&nbsp;       quantity = request.quantity,

&nbsp;       amount = request.amount,

&nbsp;       status = "PENDING",  # 待支付

&nbsp;       create\_time = datetime.now()

&nbsp;   )

&nbsp;   db.insert(order)

&nbsp;   

&nbsp;   # 4. 记录幂等token

&nbsp;   redis.setex(f"idempotent:{request.idempotent\_token}", 86400, order.order\_id)

&nbsp;   

&nbsp;   # 5. 发送消息（异步处理）

&nbsp;   mq.send("order\_created", {

&nbsp;       "order\_id": order.order\_id,

&nbsp;       "user\_id": order.user\_id,

&nbsp;       "product\_id": order.product\_id,

&nbsp;       "quantity": order.quantity

&nbsp;   })

&nbsp;   

&nbsp;   return order

2\. 订单状态机



python

\# 订单状态流转

class OrderStateMachine:

&nbsp;   # 状态定义

&nbsp;   PENDING = "pending"      # 待支付

&nbsp;   PAID = "paid"            # 已支付

&nbsp;   SHIPPED = "shipped"      # 已发货

&nbsp;   COMPLETED = "completed"  # 已完成

&nbsp;   CANCELLED = "cancelled"  # 已取消

&nbsp;   REFUNDING = "refunding"  # 退款中

&nbsp;   REFUNDED = "refunded"    # 已退款

&nbsp;   

&nbsp;   # 允许的状态转换

&nbsp;   TRANSITIONS = {

&nbsp;       PENDING: \[PAID, CANCELLED],

&nbsp;       PAID: \[SHIPPED, REFUNDING],

&nbsp;       SHIPPED: \[COMPLETED, REFUNDING],

&nbsp;       COMPLETED: \[REFUNDING],

&nbsp;       REFUNDING: \[REFUNDED],

&nbsp;       CANCELLED: \[],

&nbsp;       REFUNDED: \[]

&nbsp;   }

&nbsp;   

&nbsp;   @classmethod

&nbsp;   def transition(cls, order\_id, from\_status, to\_status):

&nbsp;       # 原子性状态更新

&nbsp;       affected = db.execute("""

&nbsp;           UPDATE orders 

&nbsp;           SET status=%s, update\_time=NOW() 

&nbsp;           WHERE id=%s AND status=%s

&nbsp;       """, to\_status, order\_id, from\_status)

&nbsp;       

&nbsp;       if affected == 0:

&nbsp;           # 状态已变，可能是并发操作

&nbsp;           current = db.get("SELECT status FROM orders WHERE id=%s", order\_id)

&nbsp;           raise StateMachineException(f"订单状态已变更: {current}")

&nbsp;       

&nbsp;       # 状态变更后发送事件

&nbsp;       mq.send("order\_status\_changed", {

&nbsp;           "order\_id": order\_id,

&nbsp;           "from": from\_status,

&nbsp;           "to": to\_status,

&nbsp;           "time": datetime.now()

&nbsp;       })

&nbsp;       return True

3\. 超时处理服务



python

\# 方案：RocketMQ延迟消息

def handle\_order\_timeout():

&nbsp;   @RocketMQListener(topic="order\_delay")

&nbsp;   def on\_message(msg):

&nbsp;       order\_id = msg.body\['order\_id']

&nbsp;       order = db.get("SELECT status FROM orders WHERE id=%s", order\_id)

&nbsp;       

&nbsp;       if order and order\['status'] == 'PENDING':

&nbsp;           # 超时取消

&nbsp;           OrderStateMachine.transition(order\_id, 'PENDING', 'CANCELLED')

&nbsp;           # 释放库存

&nbsp;           mq.send("release\_stock", {

&nbsp;               "product\_id": order\['product\_id'],

&nbsp;               "quantity": order\['quantity']

&nbsp;           })

4\. 订单查询服务



python

\# 读写分离 + 缓存

def get\_order(order\_id, user\_id):

&nbsp;   # 1. 查缓存

&nbsp;   cache\_key = f"order:{order\_id}"

&nbsp;   order = redis.get(cache\_key)

&nbsp;   if order:

&nbsp;       return json.loads(order)

&nbsp;   

&nbsp;   # 2. 查从库（读写分离）

&nbsp;   order = db\_slave.get("SELECT \* FROM orders WHERE id=%s AND user\_id=%s", 

&nbsp;                       order\_id, user\_id)

&nbsp;   if not order:

&nbsp;       return None

&nbsp;   

&nbsp;   # 3. 写缓存（5分钟过期）

&nbsp;   redis.setex(cache\_key, 300, json.dumps(order))

&nbsp;   return order



\# 订单搜索（用ES）

def search\_orders(user\_id, keyword, page, size):

&nbsp;   # 构建ES查询

&nbsp;   body = {

&nbsp;       "query": {

&nbsp;           "bool": {

&nbsp;               "must": \[

&nbsp;                   {"term": {"user\_id": user\_id}},

&nbsp;                   {"match": {"product\_name": keyword}}

&nbsp;               ]

&nbsp;           }

&nbsp;       },

&nbsp;       "from": (page-1) \* size,

&nbsp;       "size": size,

&nbsp;       "sort": \[{"create\_time": "desc"}]

&nbsp;   }

&nbsp;   result = es.search(index="orders", body=body)

&nbsp;   return result\['hits']\['hits']

数据流向设计：



写请求流程：



text

用户下单 → API网关 → 订单创建服务 → \[幂等校验] → \[Redis扣库存] → \[DB写订单] → \[发MQ] → 返回

&nbsp;                                                         ↓

&nbsp;                                                   异步消费者：

&nbsp;                                                   ├─ 扣减真实库存

&nbsp;                                                   ├─ 发送短信/推送

&nbsp;                                                   ├─ 更新ES索引

&nbsp;                                                   └─ 记录统计

读请求流程：



text

查询订单 → API网关 → 订单查询服务 → \[查Redis缓存] → (miss) → \[查DB从库] → \[回写缓存] → 返回

&nbsp;                     ↓

&nbsp;                 \[查ES] → 搜索订单

技术选型理由：



组件	选型	理由

数据库	MySQL分库分表	订单数据结构化，强一致，成熟稳定

缓存	Redis Cluster	高并发读，支持分布式锁

消息队列	RocketMQ	高吞吐，支持事务消息，延迟消息

搜索	Elasticsearch	复杂查询，全文检索

分库分表	ShardingSphere	成熟，社区活跃，支持读写分离

监控	Prometheus + Grafana	开源，生态好

链路追踪	SkyWalking	无侵入，性能好

扩展性设计：



分库分表策略



sql

-- 分片键：user\_id

-- 分16个库，每个库64张表

database = user\_id % 16

table = user\_id % 64



-- 订单号带分片信息

order\_id = timestamp + database + table + sequence

热点数据隔离



python

\# 大卖家/热门商品订单单独分库

if is\_hot\_seller(user\_id):

&nbsp;   return use\_hot\_db(user\_id)

冷热数据分离



python

\# 3个月前的订单迁移到历史库

@cron('0 2 \* \* \*')  # 每天凌晨2点

def archive\_orders():

&nbsp;   db.execute("""

&nbsp;       INSERT INTO history.orders SELECT \* FROM orders 

&nbsp;       WHERE create\_time < NOW() - INTERVAL 3 MONTH

&nbsp;   """)

&nbsp;   db.execute("""

&nbsp;       DELETE FROM orders 

&nbsp;       WHERE create\_time < NOW() - INTERVAL 3 MONTH

&nbsp;   """)

边界情况处理：



场景	处理方案

重复下单	幂等token + 唯一索引

库存超卖	Redis原子扣减 + 最终校验

订单状态不一致	状态机 + 最终一致性

MQ消息丢失	本地消息表 + 定时补偿

数据库死锁	重试机制 + 顺序加锁

缓存穿透	布隆过滤器 + 空值缓存

缓存雪崩	过期时间随机化 + 熔断

追问点：



分库分表后，跨库查询怎么办？

（答：应用层聚合，或ES宽表）



分布式事务怎么保证？

（答：最终一致性，TCC或Saga）



如果订单量增长10倍，架构怎么演进？

（答：增加分库数、引入TiDB、读写分离加从库）



第二部分：跨团队协作与疑难杂症 (15分钟)

问题2：线上突然出现大量订单状态不一致，用户反馈支付成功但订单显示未支付，你怎么排查和处理？

考察意图：



复杂问题排查能力



跨团队协作能力



应急处理能力



沟通能力



评分标准：



好：能分步骤排查（快速止血→定位原因→修复→复盘），能协调多个团队（DBA、运维、产品、测试），能给出长期解决方案



中：能说排查思路，但缺乏协作细节



差：直接甩锅给其他团队



参考答案：

（摘自后端场景面试题文档-接口变慢排查、线上CPU飙高排查）



完整排查处理流程：



第一阶段：快速止血（5分钟内）



python

\# 1. 确认问题范围和影响

\- 影响多少订单？占比多少？

\- 影响哪些用户？哪些业务线？

\- 是否还在持续发生？



\# 2. 临时措施

if emergency\_level == "critical":

&nbsp;   # 如果影响核心业务，考虑降级

&nbsp;   enable\_degrade\_mode()  # 暂停部分非核心功能

&nbsp;   

&nbsp;   # 或者手动补偿

&nbsp;   affected\_orders = get\_affected\_orders()

&nbsp;   for order in affected\_orders:

&nbsp;       # 人工补偿脚本

&nbsp;       repair\_order(order)

沟通话术：



text

我：各位同学，刚接到反馈订单状态不一致问题。我初步判断影响范围约5000个订单，集中在今天10:00-10:30。



【紧急处理】：

1\. 我已暂时关闭订单状态变更的异步处理，避免问题扩大

2\. 启动应急小组：我（开发）、DBA、运维、测试、产品

3\. 10分钟内给出初步定位



请大家配合：

\- DBA：查这个时间段的数据库慢日志和错误日志

\- 运维：查应用日志和MQ积压情况

\- 测试：协助复现问题

\- 产品：准备安抚用户的文案

第二阶段：定位原因（15-30分钟）



排查思路：



1\. 查看日志



bash

\# 应用日志

grep "order\_id=123456" app.log

grep "支付回调" app.log | grep "ERROR"



\# 慢查询日志

grep "update orders" mysql-slow.log



\# MQ日志

grep "order\_paid" mq.log

2\. 链路追踪（SkyWalking）



python

\# 追踪一笔问题订单的完整链路

trace\_id = get\_trace\_id(order\_id)

trace = skywalking.query\_trace(trace\_id)



\# 看各阶段耗时

for span in trace.spans:

&nbsp;   print(f"{span.operation}: {span.duration}ms, status={span.status}")

3\. 常见原因分析：



原因A：支付回调丢失



python

\# 支付流程：用户支付 → 第三方回调 → 订单服务更新状态

\# 可能问题：

\# - 回调超时，第三方重试但幂等没做好

\# - 回调处理异常，事务回滚了但没重试

\# - 网络问题，回调根本没到



\# 排查：查第三方回调日志

third\_party\_logs = get\_callback\_logs(order\_id)

if not third\_party\_logs:

&nbsp;   # 第三方没回调，需要和对方确认

&nbsp;   contact\_third\_party(order\_id)

原因B：数据库更新失败但没报错



python

@transactional

def handle\_paid\_callback(order\_id):

&nbsp;   # 更新订单状态

&nbsp;   affected = db.execute("""

&nbsp;       UPDATE orders SET status='PAID' 

&nbsp;       WHERE id=%s AND status='PENDING'

&nbsp;   """, order\_id)

&nbsp;   

&nbsp;   # 忘记检查affected！

&nbsp;   # 如果返回0，说明订单状态已变，可能被别人更新了

&nbsp;   

&nbsp;   mq.send("order\_paid", {"order\_id": order\_id})

原因C：MQ消息积压或丢失



python

\# 支付成功发MQ，消费者更新ES/发短信

\# 如果MQ积压几十万，状态更新延迟几小时



\# 排查

lag = kafka.get\_lag("order\_paid\_group")

if lag > 10000:

&nbsp;   print("MQ积压严重")

原因D：分布式锁失效



python

\# 并发回调时，两个请求同时更新同一个订单

\# 没加锁或锁过期时间太短



def handle\_paid(order\_id):

&nbsp;   lock\_key = f"lock:order:{order\_id}"

&nbsp;   # 获取锁，但没设置足够过期时间

&nbsp;   lock = redis.setnx(lock\_key, "1")  # 没设置过期！

&nbsp;   

&nbsp;   if lock:

&nbsp;       update\_order(order\_id)

&nbsp;       redis.delete(lock\_key)

&nbsp;   else:

&nbsp;       # 等待重试

&nbsp;       pass

第三阶段：修复和补偿（30-60分钟）



1\. 修复代码问题



python

\# 改进1：加锁并设置过期时间

def handle\_paid\_safe(order\_id):

&nbsp;   lock\_key = f"lock:order:{order\_id}"

&nbsp;   # SET NX PX 原子操作

&nbsp;   locked = redis.set(lock\_key, "1", nx=True, px=10000)  # 10秒过期

&nbsp;   

&nbsp;   if not locked:

&nbsp;       # 加入重试队列

&nbsp;       mq.send\_with\_delay("retry\_queue", {"order\_id": order\_id}, delay=1000)

&nbsp;       return

&nbsp;   

&nbsp;   try:

&nbsp;       # 处理业务

&nbsp;       affected = db.execute("""

&nbsp;           UPDATE orders SET status='PAID' 

&nbsp;           WHERE id=%s AND status='PENDING'

&nbsp;       """, order\_id)

&nbsp;       

&nbsp;       if affected == 0:

&nbsp;           # 状态已变，可能是并发，记录日志

&nbsp;           logger.warning(f"订单{order\_id}状态已变，当前状态: {get\_order\_status(order\_id)}")

&nbsp;       else:

&nbsp;           mq.send("order\_paid", {"order\_id": order\_id})

&nbsp;   finally:

&nbsp;       # 释放锁（Lua保证原子性）

&nbsp;       redis.eval("""

&nbsp;           if redis.call('get', KEYS\[1]) == ARGV\[1] then

&nbsp;               return redis.call('del', KEYS\[1])

&nbsp;           else

&nbsp;               return 0

&nbsp;           end

&nbsp;       """, 1, lock\_key, "1")

2\. 补偿脚本



python

def compensate\_orders():

&nbsp;   # 找出不一致的订单

&nbsp;   # 条件：支付记录存在但订单状态不是PAID

&nbsp;   abnormal\_orders = db.execute("""

&nbsp;       SELECT o.id, p.pay\_time 

&nbsp;       FROM orders o 

&nbsp;       INNER JOIN payments p ON o.id = p.order\_id

&nbsp;       WHERE o.status != 'PAID' 

&nbsp;       AND p.status = 'SUCCESS'

&nbsp;   """)

&nbsp;   

&nbsp;   for order in abnormal\_orders:

&nbsp;       try:

&nbsp;           # 修复订单状态

&nbsp;           db.execute("""

&nbsp;               UPDATE orders SET status='PAID', update\_time=NOW() 

&nbsp;               WHERE id=%s

&nbsp;           """, order\['id'])

&nbsp;           

&nbsp;           # 补偿后续流程

&nbsp;           mq.send("order\_paid\_compensate", {"order\_id": order\['id']})

&nbsp;           

&nbsp;           logger.info(f"补偿订单{order\['id']}成功")

&nbsp;       except Exception as e:

&nbsp;           logger.error(f"补偿订单{order\['id']}失败: {e}")

&nbsp;           # 加入人工处理队列

&nbsp;           manual\_queue.add(order\['id'])

第四阶段：复盘与长期方案（事后）



复盘文档要点：



text

【问题复盘】2024-03-15 订单状态不一致故障



一、故障现象

\- 时间：2024-03-15 10:00-10:30

\- 影响：5342个订单支付成功但状态未更新

\- 影响范围：约3%的订单



二、根本原因

\- 支付回调处理代码中，未检查UPDATE的affected rows

\- 并发情况下，两个回调同时处理同一个订单，导致状态被覆盖

\- 分布式锁未设置过期时间，导致死锁



三、处理过程

\- 10:05 收到反馈，启动应急

\- 10:15 定位问题原因

\- 10:30 修复代码并上线

\- 11:00 补偿脚本执行完成



四、改进措施

1\. 代码层

&nbsp;  - 所有UPDATE必须检查affected rows

&nbsp;  - 分布式锁统一使用Redisson，设置合理过期时间

&nbsp;  - 关键操作加日志和监控



2\. 测试层

&nbsp;  - 增加并发测试用例

&nbsp;  - 增加故障注入测试



3\. 监控层

&nbsp;  - 增加订单状态不一致监控，每分钟扫描

&nbsp;  - 增加MQ积压告警



4\. 流程层

&nbsp;  - 支付回调必须有完善的日志

&nbsp;  - 建立故障演练机制

跨团队协作要点：



团队	协作内容	沟通方式

产品	同步故障影响，准备用户补偿方案	紧急会议

运营	安抚用户，收集反馈	运营群

测试	协助复现，验证修复	即时沟通

DBA	查数据库日志，协助数据修复	电话

运维	查服务器日志，协助回滚	电话

第三方	确认回调是否正常发送	邮件/电话

追问点：



如果第三方不配合怎么办？

（答：以我方日志为准，用户发起申诉时人工处理）



补偿过程中出现新问题怎么办？

（答：暂停补偿，人工介入，保证不再扩大影响）



问题3：运营紧急需求：需要导出最近一年所有订单数据做分析，但数据量有5亿条，直接导会拖垮数据库，你怎么沟通和解决？

考察意图：



业务需求与技术限制的平衡能力



沟通技巧



技术方案设计能力



评分标准：



好：先沟通需求细节，评估技术风险，给出多个可行方案（离线数仓、分批导出、数据抽样），说明各方案优缺点，让业务做选择



中：直接给一个方案，不问需求



差：直接说“导不了”或盲目答应导致故障



参考答案：

\*（摘自Python沟通能力考察文档-问题1、后端场景-导出excel场景优化）\*



完整沟通处理流程：



第一步：澄清需求（沟通）



text

我：运营同学你好，收到你“导出一年订单数据”的需求。为了更高效地处理，我想确认几个细节：



1\. 具体需要哪些字段？全部字段还是部分？（避免导出无用数据）

2\. 数据用来做什么分析？需要实时最新数据，还是离线分析就行？

3\. 对数据完整性要求多高？必须是100%完整，还是抽样也能接受？

4\. 什么时候要？紧急程度如何？

5\. 导出后数据怎么用？Excel、Python分析还是导入BI工具？



运营：需要最近一年所有订单做用户消费行为分析，字段最好全一些，明天就要。

第二步：评估技术风险



python

\# 数据量评估

一年订单数 = 5亿条

每条订单平均大小 = 500字节（JSON格式）

总数据量 = 5亿 \* 500 ≈ 250GB



\# 技术风险

1\. 全量查询会拖垮数据库（5亿条全表扫描）

2\. 内存不够（应用服务器内存一般32GB）

3\. 网络传输250GB，带宽100Mbps需要约5小时

4\. 生成的文件用户无法打开（Excel最大104万行）

第三步：给出多个方案（让业务做选择题）



方案1：离线数仓导出（推荐）



python

"""

方案A：离线数仓导出



流程：

1\. 从离线数仓（Hive/ClickHouse）导出，不压线上库

2\. 生成CSV文件，分片压缩

3\. 提供下载链接



优点：

\- 不影响线上业务

\- 可以处理任意大数据量

\- 支持复杂分析



缺点：

\- 数据有延迟（T+1，昨天的数据）

\- 需要数仓团队配合



时间：今天能开始导出，明天上午完成

"""

方案2：分批导出 + 抽样（折中）



python

"""

方案B：分批导出 + 抽样



流程：

1\. 按时间分批，每次导100万条

2\. 生成多个CSV文件，打包下载

3\. 如果只是想看趋势，可以抽样10%



优点：

\- 对线上压力小

\- 灵活可控



缺点：

\- 需要手动合并文件

\- 不是实时数据



时间：今晚开始导出，明早完成

"""

方案3：API + 脚本拉取（灵活）



python

"""

方案C：API + 脚本拉取



流程：

1\. 我写一个导出API，支持按时间范围、字段筛选

2\. 你写Python脚本循环调用，每次拉取10万条

3\. 本地合并分析



优点：

\- 最灵活，可以随时拉取

\- 可以增量更新



缺点：

\- 需要你写脚本（我可以提供示例）

\- 总体时间较长



时间：今天给你API文档和示例代码

"""

第四步：技术实现（以方案2为例）



python

\# 分批导出脚本

def export\_orders\_batch(start\_date, end\_date, batch\_size=1000000):

&nbsp;   offset = 0

&nbsp;   batch\_num = 0

&nbsp;   

&nbsp;   while True:

&nbsp;       # 分批查询（用游标，不用OFFSET）

&nbsp;       orders = db.execute("""

&nbsp;           SELECT id, user\_id, product\_id, amount, create\_time 

&nbsp;           FROM orders 

&nbsp;           WHERE create\_time BETWEEN %s AND %s

&nbsp;           AND id > %s

&nbsp;           ORDER BY id

&nbsp;           LIMIT %s

&nbsp;       """, start\_date, end\_date, last\_id, batch\_size)

&nbsp;       

&nbsp;       if not orders:

&nbsp;           break

&nbsp;       

&nbsp;       # 写入CSV

&nbsp;       filename = f"orders\_batch\_{batch\_num}.csv"

&nbsp;       write\_to\_csv(orders, filename)

&nbsp;       

&nbsp;       # 压缩

&nbsp;       compress\_file(filename)

&nbsp;       

&nbsp;       batch\_num += 1

&nbsp;       last\_id = orders\[-1]\['id']

&nbsp;       

&nbsp;       # 暂停一下，给数据库喘息时间

&nbsp;       time.sleep(5)

&nbsp;   

&nbsp;   # 生成下载链接

&nbsp;   download\_url = upload\_to\_oss(compress\_files)

&nbsp;   return download\_url

第五步：管理预期



text

我：方案A和B都可以，我建议用方案A（数仓导出），不影响线上，数据也准。



【时间预估】

\- 今晚8点开始导出

\- 明早9点前可以完成

\- 下载链接会发你邮箱



【注意事项】

\- 导出的数据是CSV格式，5亿条会分成约50个文件

\- 总大小约50GB（压缩后），下载可能需要一些时间

\- 如果需要后续定期分析，建议接入数仓建表



您看选哪个方案？如果选A，我去和数仓团队协调。

第六步：后续优化（防止类似需求）



python

\# 1. 建立离线数仓，定期同步

@cron('0 3 \* \* \*')  # 每天凌晨3点

def sync\_to\_warehouse():

&nbsp;   # 同步前一天数据到数仓

&nbsp;   data = db.execute("SELECT \* FROM orders WHERE create\_time >= CURDATE() - INTERVAL 1 DAY")

&nbsp;   hive.execute("INSERT INTO orders\_ods VALUES ...", data)



\# 2. 提供自助导出工具

class SelfExportTool:

&nbsp;   def export(self, user\_id, sql, email):

&nbsp;       # 用户写SQL，后台异步执行

&nbsp;       task\_id = generate\_task\_id()

&nbsp;       mq.send("export\_task", {

&nbsp;           "task\_id": task\_id,

&nbsp;           "sql": sql,

&nbsp;           "email": email

&nbsp;       })

&nbsp;       return task\_id



\# 3. 数据抽样接口

def get\_sample\_orders(rate=0.1):

&nbsp;   # 随机抽样10%

&nbsp;   return db.execute("""

&nbsp;       SELECT \* FROM orders 

&nbsp;       WHERE RAND() < %s

&nbsp;   """, rate)

追问点：



如果运营坚持要实时全量数据怎么办？

（答：说明技术风险，协调产品一起沟通，必要时升级决策）



导出过程中数据库压力还是大怎么办？

（答：用从库查询，加限流，错峰执行）



第三部分：技术前瞻性与选型 (15分钟)

问题4：你们现在的技术栈是Python + MySQL + Redis + RabbitMQ。如果业务量再增长10倍，你觉得当前架构的瓶颈会在哪里？你会怎么演进？

考察意图：



架构演进规划能力



技术前瞻性



瓶颈预判能力



新技术选型能力



评分标准：



好：能系统分析各层的潜在瓶颈（数据库、缓存、MQ、应用），给出具体的演进方案和新技术选型（分库分表、TiDB、Pulsar、Service Mesh），说明迁移路径和风险



中：能说出一部分瓶颈，但演进方案不具体



差：说“加机器就行”



参考答案：



当前架构：



text

Python应用 + MySQL + Redis + RabbitMQ

QPS: 5000, 数据量: 10TB

增长10倍后的挑战：



1\. 数据库层瓶颈



指标	当前	10倍后	问题

QPS	5000	50000	连接数不够，CPU打满

数据量	10TB	100TB	单库存不下，备份恢复慢

写入	1000/s	10000/s	binlog同步延迟，死锁增多

复杂查询	100/s	1000/s	索引失效，慢查询增多

演进方案：分库分表 → TiDB



python

\# 阶段1：分库分表（ShardingSphere）

"""

分片策略：

\- 分片键：user\_id

\- 16个库，每个库64张表

\- 订单号带分片信息



优点：

\- 水平扩展

\- 成熟稳定



缺点：

\- 跨库查询复杂

\- 扩容麻烦

\- 需要改代码

"""



\# 阶段2：TiDB（最终目标）

"""

TiDB是分布式NewSQL，兼容MySQL协议



优点：

\- 自动分片，无限水平扩展

\- 支持分布式事务

\- 兼容MySQL，迁移成本低

\- 强一致，高可用



迁移路径：

1\. 用TiDB Data Migration (DM) 同步MySQL数据

2\. 灰度切读流量

3\. 双写一段时间

4\. 全量切换

"""

2\. 缓存层瓶颈



指标	当前	10倍后	问题

QPS	20000	200000	Redis单实例扛不住

内存	32GB	320GB	单机内存不够

带宽	100Mbps	1Gbps	网卡打满

演进方案：Redis Cluster → 多级缓存



python

\# 阶段1：Redis Cluster（已经支持）

"""

\- 16384个槽，自动分片

\- 支持水平扩展

\- 客户端smart client或代理



配置：

cluster-enabled yes

cluster-config-file nodes.conf

cluster-node-timeout 5000

"""



\# 阶段2：多级缓存

"""

【浏览器缓存】静态资源

&nbsp;   ↓

【CDN缓存】图片、CSS、JS

&nbsp;   ↓

【Nginx本地缓存】lua\_shared\_dict

&nbsp;   ↓

【应用本地缓存】Caffeine/CacheManager

&nbsp;   ↓

【分布式缓存】Redis Cluster

&nbsp;   ↓

【数据库】

"""



\# 本地缓存示例（Caffeine）

@Cacheable(value="orders", key="#orderId")

def get\_order(order\_id):

&nbsp;   # 先从Caffeine查

&nbsp;   order = caffeine.get(f"order:{order\_id}")

&nbsp;   if order:

&nbsp;       return order

&nbsp;   

&nbsp;   # 再从Redis查

&nbsp;   order = redis.get(f"order:{order\_id}")

&nbsp;   if order:

&nbsp;       caffeine.put(f"order:{order\_id}", order, 60)  # 本地缓存1分钟

&nbsp;       return order

&nbsp;   

&nbsp;   # 最后查DB

&nbsp;   order = db.query(...)

&nbsp;   redis.setex(f"order:{order\_id}", 3600, order)

&nbsp;   caffeine.put(f"order:{order\_id}", order, 60)

&nbsp;   return order

3\. 消息队列瓶颈



指标	当前	10倍后	问题

TPS	2000	20000	RabbitMQ单机瓶颈

堆积	10万	100万	磁盘IO压力大

延迟	10ms	100ms	消费跟不上

演进方案：RabbitMQ → RocketMQ/Kafka/Pulsar



方案	吞吐	延迟	特性	适用场景

RabbitMQ	万级	微秒	路由灵活	业务解耦

RocketMQ	十万级	毫秒	事务消息	电商交易

Kafka	百万级	毫秒	持久化，流处理	日志收集

Pulsar	百万级	毫秒	存算分离，多租户	云原生

python

\# 选型建议：

"""

\- 核心交易消息：用RocketMQ（事务消息，可靠）

\- 日志/埋点：用Kafka（高吞吐）

\- 云原生环境：用Pulsar（存算分离）



迁移策略：

1\. 引入消息网关，对业务屏蔽底层MQ

2\. 逐步迁移topic

3\. 双写+对比验证

"""

4\. 应用层瓶颈



指标	当前	10倍后	问题

实例数	10	100	运维复杂

部署	手动	自动	发布慢

监控	基础	全面	定位难

治理	无	需要	服务发现、熔断

演进方案：微服务 + Service Mesh



python

\# 阶段1：微服务化

"""

拆分原则：

\- 按业务域拆分（订单、用户、库存、支付）

\- 每个服务独立数据库

\- 服务间通过API/消息通信



技术栈：

\- 注册中心：Nacos/Eureka

\- 配置中心：Apollo/Nacos

\- 网关：Spring Cloud Gateway

\- RPC：Dubbo/gRPC

"""



\# 阶段2：容器化 + Kubernetes

"""

\- 所有服务容器化

\- K8s管理部署和扩缩容

\- HPA自动扩缩容

"""



\# 阶段3：Service Mesh（Istio）

"""

\- 将服务治理下沉到Sidecar

\- 流量管理、熔断、限流配置化

\- 可观测性增强

"""

5\. 监控运维瓶颈



指标	当前	10倍后	问题

日志	10GB/天	100GB/天	存储和分析难

监控指标	100个	1000个	告警太多

链路追踪	无	需要	定位慢请求难

演进方案：ELK + Prometheus + SkyWalking



python

"""

监控体系三层：

1\. 指标监控：Prometheus + Grafana

&nbsp;  - 系统指标（CPU、内存、磁盘）

&nbsp;  - 业务指标（QPS、响应时间、错误率）

&nbsp;  - 中间件指标（MQ积压、Redis命中率）



2\. 日志聚合：ELK/EFK

&nbsp;  - Filebeat采集日志

&nbsp;  - Logstash处理

&nbsp;  - Elasticsearch存储

&nbsp;  - Kibana展示



3\. 链路追踪：SkyWalking/Pinpoint

&nbsp;  - 无侵入

&nbsp;  - 跨服务追踪

&nbsp;  - 性能分析

"""

演进路线图：



text

阶段1（3个月）：分库分表 + Redis Cluster

阶段2（6个月）：服务拆分 + 容器化

阶段3（12个月）：TiDB + Service Mesh

阶段4（18个月）：云原生 + 混合云

追问点：



为什么选TiDB而不是CockroachDB？

（答：MySQL兼容性好，社区活跃，国内支持好）



微服务拆分后，分布式事务怎么处理？

（答：最终一致性，TCC或Saga）



第四部分：技术领导力 (15分钟)

问题5：你作为技术负责人，团队来了一个新人，经验不足但很有潜力，你怎么带他？

考察意图：



领导力和带人能力



耐心和沟通方式



人才培养意识



评分标准：



好：能给出系统的新人培养方案（技术、业务、流程），关注新人的成长和困惑，给予适当挑战和支持



中：能说一些基本方法，但不系统



差：说“让他自己学”或“给他简单任务”



参考答案：

\*（摘自Python沟通能力考察文档-问题42）\*



新人培养四阶段法：



第一阶段：入职引导（第1周）



python

"""

【目标】了解团队、熟悉环境、建立信心



1\. 第一天：欢迎和介绍

&nbsp;  - 介绍团队成员

&nbsp;  - 介绍项目背景和业务

&nbsp;  - 介绍开发流程和规范

&nbsp;  - 配开发环境



2\. 第二天-第三天：文档阅读

&nbsp;  - 架构设计文档

&nbsp;  - 接口文档

&nbsp;  - 数据库ER图

&nbsp;  - 代码规范



3\. 第四天-第五天：跟着我结对编程

&nbsp;  - 我演示一个功能的完整开发流程

&nbsp;  - 新人提问，我解答

&nbsp;  - 新人尝试写一个小功能

"""

沟通话术：



text

我：欢迎加入团队！这一周你的目标是熟悉环境，不用着急产出。



我给你准备了几份文档：

\- 《项目架构设计v2.0》

\- 《接口文档》

\- 《数据库设计》

\- 《开发规范》



每天下午4点我们聊一下进展，有什么问题随时问我。



周五我们一起做一个小的功能，让你感受一下完整流程。

第二阶段：基础培养（第2-4周）



python

"""

【目标】掌握核心技能，独立完成简单任务



1\. 任务设计

&nbsp;  - 从简单开始：修改一个小功能、修复一个bug

&nbsp;  - 逐步增加难度：开发一个新接口、写单元测试

&nbsp;  - 确保任务明确、有文档、有示例



2\. Code Review

&nbsp;  - 每次提交代码都要review

&nbsp;  - 先说优点，再提建议

&nbsp;  - 解释为什么这么写更好



3\. 每周1:1

&nbsp;  - 聊技术问题

&nbsp;  - 聊职业困惑

&nbsp;  - 聊团队融入

"""

Code Review示例：



text

我：这个功能实现得很好，逻辑清晰，测试也写了。👍



有几个小建议：

1\. 变量名`a`可以改成更明确的`order\_status`，这样别人更容易看懂

2\. 这里可以加个try-except，处理数据库超时的情况

3\. 可以加个注释，说明这个函数的业务含义



你看需要我帮忙改吗？或者我们一起优化一下？

第三阶段：能力提升（第2-3个月）



python

"""

【目标】独立负责小模块，参与设计



1\. 独立任务

&nbsp;  - 负责一个小模块的全流程开发

&nbsp;  - 从需求分析到上线

&nbsp;  - 有问题可以先自己查，查不到再问我



2\. 设计参与

&nbsp;  - 邀请他参加技术方案讨论

&nbsp;  - 鼓励他提出自己的看法

&nbsp;  - 即使想法不成熟，也先肯定再引导



3\. 技术分享

&nbsp;  - 鼓励他做一次技术分享

&nbsp;  - 主题可以是他最近学到的

&nbsp;  - 帮他准备和演练

"""

第四阶段：成长加速（3-6个月）



python

"""

【目标】独当一面，带新人的新人



1\. 复杂任务

&nbsp;  - 挑战性任务，如性能优化、架构调整

&nbsp;  - 需要跨团队协作

&nbsp;  - 需要自己做技术决策



2\. 指导他人

&nbsp;  - 有机会可以让他指导更新的新人

&nbsp;  - 教是最好的学



3\. 技术深耕

&nbsp;  - 根据兴趣选择方向深入

&nbsp;  - 提供学习资源和机会

&nbsp;  - 鼓励参加技术会议

"""

新人常见问题及应对：



问题	应对

不敢问问题	定期1:1，创造安全的提问环境

代码看不懂	结对编程，一起过代码

不知道优先级	明确任务优先级，解释为什么

犯错不敢说	先解决问题，再复盘，不指责

技术选择困难	给出建议，解释理由，让他自己做决定

培养新人的原则：



耐心 - 新人问的问题再基础也要认真回答



信任 - 给他独立负责的机会，信任他能做好



支持 - 遇到困难时，提供帮助而不是替他做



反馈 - 及时给出正面和建设性反馈



榜样 - 自己做好榜样，代码规范、沟通方式



追问点：



如果新人进步慢怎么办？

（答：找原因，是能力问题还是态度问题，针对性帮助）



如果新人犯了大错导致线上故障怎么办？

（答：先解决问题，再复盘流程，不指责个人）



问题6：团队里有两个同事技术方案有分歧，谁也说服不了谁，你作为技术负责人怎么处理？

考察意图：



冲突解决能力



决策能力



沟通协调能力



评分标准：



好：能引导双方聚焦问题而非立场，组织技术讨论会，列出各方案的优缺点，引入数据和实验验证，必要时自己做决策并说明理由



中：说“我拍板”但没说服过程



差：回避问题，让矛盾激化



参考答案：

（摘自Python沟通能力考察文档-冲突处理相关）



处理流程：



第一步：分别沟通，了解立场



text

和同事A聊：

我：听说你和B在方案上有不同看法。我想听听你的方案和理由，为什么你觉得你的方案更好？



A：我觉得用RabbitMQ更合适，因为我们团队熟悉，出问题好排查。



和同事B聊：

我：你的方案是Kafka，能说说你的考虑吗？



B：Kafka吞吐更高，而且我们未来数据量会很大，RabbitMQ可能扛不住。

第二步：组织技术讨论会



python

"""

会议议程：

1\. 双方各15分钟阐述方案（只说事实，不人身攻击）

2\. 现场提问环节

3\. 列出对比表

4\. 决定下一步

"""

对比表示例：



维度	方案A (RabbitMQ)	方案B (Kafka)

吞吐量	万级/秒	百万级/秒

延迟	微秒级	毫秒级

消息顺序	单队列有序	分区内有序

消息路由	丰富	简单

持久化	内存+磁盘	磁盘

团队熟悉度	高	低

运维成本	低	中

扩展性	一般	好

第三步：引入数据和实验



text

我：理论对比都有道理，我们用数据说话。



建议：

1\. 明天各做一个POC，用真实业务场景压测

2\. 重点关注：

&nbsp;  - 峰值吞吐能达到多少

&nbsp;  - 消息延迟P99

&nbsp;  - 资源消耗

&nbsp;  - 出问题时的表现



3\. 后天我们再看结果

第四步：决策



python

"""

基于POC结果：

\- RabbitMQ: 峰值5000 TPS，延迟10ms

\- Kafka: 峰值5万 TPS，延迟50ms



我们的业务场景：

\- 目前峰值2000 TPS

\- 预计3年内增长到1万 TPS

\- 对延迟敏感（要求<50ms）

\- 需要灵活路由（不同事件类型）



结论：

\- 短期用RabbitMQ（团队熟悉，延迟低）

\- 长期预留升级到Kafka的路径

\- 在消息网关层做好抽象，方便未来切换

"""

沟通话术：



text

我：感谢两位的深入探讨，让我们对方案有了更全面的认识。



基于POC结果和业务需求，我的决定是：



先用RabbitMQ，因为：

1\. 当前吞吐量完全够用

2\. 延迟更低，符合业务要求

3\. 团队熟悉，上线快，风险低



但同时，我们要：

1\. 消息客户端抽象，不依赖具体MQ

2\. 监控到位，知道什么时候需要升级

3\. 定期评估Kafka，做好技术储备



这个决定可能不完全偏向任何一方，但我觉得是目前最稳妥的选择。大家有意见吗？

第五步：共识和执行



text

我：既然大家没意见，我们就按这个执行。



A负责RabbitMQ的详细设计和开发

B负责消息客户端的抽象和监控设计



下周我们review进展。



感谢两位的贡献，你们的讨论帮我们避免了很多坑。

处理原则：



对事不对人 - 聚焦问题本身



数据说话 - 避免主观争论



求同存异 - 找到共同目标



尊重专业 - 承认双方都有道理



明确决策 - 最后必须有人拍板



公平公正 - 不偏袒任何一方



追问点：



如果一方坚持不接受怎么办？

（答：尊重个人意见，但团队需要一致行动，可以给他机会继续收集数据，但执行必须统一）



如果后来证明决策错了怎么办？

（答：复盘为什么错，及时调整，不甩锅）



第五部分：价值观与软技能 (10分钟)

问题7：你经历过最失败的项目是什么？从中学到了什么？

考察意图：



自我认知和反思能力



面对失败的态度



学习和成长能力



评分标准：



好：能坦诚说出失败经历，分析失败原因，总结具体学到了什么，如何应用到后续工作中



中：能说出失败，但反思不深



差：说“没失败过”或把失败归咎于别人



参考答案：



失败案例：



text

【项目背景】

我之前负责一个用户画像系统，需要每天处理10亿条用户行为数据，生成用户标签。



【失败经过】

项目上线后，经常凌晨3-4点挂掉，第二天早上运营发现数据不对。连续一周都在半夜被叫起来修。



【失败原因】



1\. 技术层面

&nbsp;  - 高估了单机处理能力：用单机Python处理10亿数据

&nbsp;  - 内存没控制好：pandas加载数据时内存爆了

&nbsp;  - 没有熔断机制：一个环节挂了，整个流程卡死

&nbsp;  - 监控不完善：出问题时才知道，没有提前预警



2\. 流程层面

&nbsp;  - 压测不充分：只在1亿数据上测过

&nbsp;  - 上线前没做全链路演练

&nbsp;  - 没有回滚预案



3\. 沟通层面

&nbsp;  - 没和运营对齐预期：他们说“实时”，我以为几分钟，其实是几小时

&nbsp;  - 问题发生时，沟通混乱，不知道谁负责

学到的教训：



python

"""

1\. 技术设计层面

&nbsp;  - 任何系统都要假设会失败，设计容错和降级

&nbsp;  - 大数据处理必须用分布式，不能用单机

&nbsp;  - 监控要全：CPU、内存、磁盘、业务指标

&nbsp;  - 关键数据要有备份和可重算机制



2\. 项目管理层面

&nbsp;  - 压测要到真实数据量的1.5倍

&nbsp;  - 上线前必须做全链路演练

&nbsp;  - 要有完善的回滚方案

&nbsp;  - 设定明确的上线标准和检查清单



3\. 沟通协作层面

&nbsp;  - 需求必须对齐：什么是“实时”，什么算“完成”

&nbsp;  - 明确故障响应流程：谁负责，怎么通知，怎么处理

&nbsp;  - 定期同步进度，暴露风险

"""

改进后的成果：



python

"""

后来重建了这个系统：

\- 用Spark分布式处理

\- 加了完善的监控和告警

\- 做了全链路压测和演练

\- 和运营对齐了SLA：T+1数据，每天8点前可用



系统稳定运行一年，没有再出过问题。

"""

总结：



text

这次失败让我深刻理解：技术只是手段，不是目的。一个成功的系统需要技术、流程、沟通三者结合。



现在我做任何项目，都会先问：

1\. 业务真正需要什么？

2\. 哪里可能出问题？

3\. 出了问题怎么办？



这些思考比技术本身更重要。

追问点：



如果再来一次，你会怎么做？

（答：按上面改进方案做）



这个教训现在怎么用？

（答：评审方案时会主动问“故障场景怎么处理”）



第六部分：业务理解与商业思维 (10分钟)

问题8：如果让你设计一个优惠券系统，需要考虑哪些业务规则？怎么保证不超发、不重复使用？

考察意图：



业务理解能力



复杂业务建模能力



技术方案设计



评分标准：



好：能全面考虑优惠券的各种业务规则（领取条件、使用范围、有效期、叠加规则、风控），给出技术实现方案，说明如何保证数据一致性



中：能说一部分规则，但技术方案不完整



差：只说“发券和用券”



参考答案：



优惠券核心业务规则：



python

"""

1\. 发放规则

&nbsp;  - 谁可以领？新用户、老用户、指定人群

&nbsp;  - 怎么领？主动领取、系统发放、活动赠送

&nbsp;  - 领取限制？每人限领几张、总量限制



2\. 使用规则

&nbsp;  - 适用商品：全平台、指定品类、指定商品

&nbsp;  - 适用金额：满减、折扣、立减

&nbsp;  - 叠加规则：能否和其他优惠叠加

&nbsp;  - 使用时间：有效期、可用时段



3\. 风控规则

&nbsp;  - 防刷：同一IP限领、设备指纹

&nbsp;  - 异常使用：大额订单拆单、恶意套现

"""

系统架构：



text

【发放服务】 → \[Redis] ← 用户领券

&nbsp;   ↓

【核销服务】 → \[Redis] ← 用户用券

&nbsp;   ↓

【MQ】 → 【对账服务】

&nbsp;   ↓

【MySQL】 ← 最终数据落地

核心表设计：



sql

-- 券模板表（定义优惠券规则）

CREATE TABLE coupon\_template (

&nbsp;   id BIGINT PRIMARY KEY,

&nbsp;   name VARCHAR(100),

&nbsp;   type TINYINT,  -- 1:满减 2:折扣 3:立减

&nbsp;   condition\_amount DECIMAL(10,2),  -- 满多少可用

&nbsp;   discount\_amount DECIMAL(10,2),   -- 减多少

&nbsp;   discount\_rate DECIMAL(3,2),       -- 折扣率

&nbsp;   total\_quantity INT,               -- 总发放量

&nbsp;   per\_user\_limit INT,                -- 每人限领

&nbsp;   start\_time DATETIME,

&nbsp;   end\_time DATETIME,

&nbsp;   status TINYINT                     -- 0:待生效 1:生效中 2:已结束

);



-- 用户券表

CREATE TABLE user\_coupon (

&nbsp;   id BIGINT PRIMARY KEY,

&nbsp;   coupon\_code VARCHAR(32) UNIQUE,   -- 券码

&nbsp;   template\_id BIGINT,

&nbsp;   user\_id BIGINT,

&nbsp;   status TINYINT,                   -- 0:未使用 1:已使用 2:已过期 3:已冻结

&nbsp;   receive\_time DATETIME,

&nbsp;   use\_time DATETIME,

&nbsp;   order\_id BIGINT,                   -- 使用的订单

&nbsp;   INDEX idx\_user (user\_id),

&nbsp;   INDEX idx\_status (status)

);



-- 券使用记录

CREATE TABLE coupon\_usage\_log (

&nbsp;   id BIGINT PRIMARY KEY,

&nbsp;   coupon\_code VARCHAR(32),

&nbsp;   order\_id BIGINT,

&nbsp;   user\_id BIGINT,

&nbsp;   use\_time DATETIME,

&nbsp;   status VARCHAR(20)

);

防止超发：



python

\# 发券时用Redis原子计数

def receive\_coupon(user\_id, template\_id):

&nbsp;   # 1. 检查总量

&nbsp;   key\_count = f"coupon:template:{template\_id}:count"

&nbsp;   current = redis.incr(key\_count)

&nbsp;   

&nbsp;   if current == 1:

&nbsp;       # 第一次，设置过期时间（券活动结束后自动删除）

&nbsp;       template = get\_template(template\_id)

&nbsp;       expire\_seconds = (template.end\_time - datetime.now()).seconds

&nbsp;       redis.expire(key\_count, expire\_seconds)

&nbsp;   

&nbsp;   # 从模板获取总量

&nbsp;   template = get\_template(template\_id)

&nbsp;   if current > template.total\_quantity:

&nbsp;       redis.decr(key\_count)  # 回滚

&nbsp;       return {"code": 400, "message": "券已领完"}

&nbsp;   

&nbsp;   # 2. 检查用户限领

&nbsp;   key\_user = f"coupon:template:{template\_id}:user:{user\_id}"

&nbsp;   user\_count = redis.incr(key\_user)

&nbsp;   if user\_count == 1:

&nbsp;       redis.expire(key\_user, expire\_seconds)

&nbsp;   

&nbsp;   if user\_count > template.per\_user\_limit:

&nbsp;       redis.decr(key\_user)

&nbsp;       return {"code": 400, "message": "已达领取上限"}

&nbsp;   

&nbsp;   # 3. 生成券码

&nbsp;   coupon\_code = generate\_coupon\_code()

&nbsp;   

&nbsp;   # 4. 异步落库

&nbsp;   mq.send("coupon\_receive", {

&nbsp;       "coupon\_code": coupon\_code,

&nbsp;       "user\_id": user\_id,

&nbsp;       "template\_id": template\_id,

&nbsp;       "receive\_time": datetime.now()

&nbsp;   })

&nbsp;   

&nbsp;   return {"code": 0, "message": "领取成功", "coupon\_code": coupon\_code}

防止重复使用：



python

\# 用券时保证原子性

def use\_coupon(coupon\_code, order\_id, user\_id):

&nbsp;   # 1. Redis原子状态变更

&nbsp;   key = f"coupon:code:{coupon\_code}"

&nbsp;   

&nbsp;   # SET NX + Lua保证原子性

&nbsp;   lua = """

&nbsp;   local status = redis.call('get', KEYS\[1])

&nbsp;   if status == false then

&nbsp;       return -1  -- 券不存在

&nbsp;   end

&nbsp;   if status ~= '0' then

&nbsp;       return 0   -- 已使用或已过期

&nbsp;   end

&nbsp;   redis.call('set', KEYS\[1], '1')

&nbsp;   return 1

&nbsp;   """

&nbsp;   

&nbsp;   result = redis.eval(lua, 1, key)

&nbsp;   

&nbsp;   if result <= 0:

&nbsp;       return {"code": 400, "message": "券不可用"}

&nbsp;   

&nbsp;   # 2. 更新订单优惠

&nbsp;   apply\_discount(order\_id, coupon\_code)

&nbsp;   

&nbsp;   # 3. 异步落库

&nbsp;   mq.send("coupon\_use", {

&nbsp;       "coupon\_code": coupon\_code,

&nbsp;       "order\_id": order\_id,

&nbsp;       "user\_id": user\_id,

&nbsp;       "use\_time": datetime.now()

&nbsp;   })

&nbsp;   

&nbsp;   return {"code": 0, "message": "使用成功"}

防刷设计：



python

def anti\_brush\_check(user\_id, ip, device\_id):

&nbsp;   # 1. IP限流

&nbsp;   ip\_key = f"anti:ip:{ip}"

&nbsp;   ip\_count = redis.incr(ip\_key)

&nbsp;   if ip\_count == 1:

&nbsp;       redis.expire(ip\_key, 3600)  # 1小时

&nbsp;   if ip\_count > 100:

&nbsp;       return False

&nbsp;   

&nbsp;   # 2. 设备限流

&nbsp;   device\_key = f"anti:device:{device\_id}"

&nbsp;   device\_count = redis.incr(device\_key)

&nbsp;   if device\_count == 1:

&nbsp;       redis.expire(device\_key, 3600)

&nbsp;   if device\_count > 50:

&nbsp;       return False

&nbsp;   

&nbsp;   # 3. 用户行为异常检测

&nbsp;   # 短时间内领了太多券

&nbsp;   recent = redis.zcount(f"user:{user\_id}:receive", time.time()-3600, time.time())

&nbsp;   if recent > 10:

&nbsp;       return False

&nbsp;   

&nbsp;   return True

过期处理：



python

\# 定时任务扫描过期券

@cron('0 2 \* \* \*')  # 每天凌晨2点

def expire\_coupons():

&nbsp;   db.execute("""

&nbsp;       UPDATE user\_coupon 

&nbsp;       SET status=2 

&nbsp;       WHERE status=0 AND expire\_time < NOW()

&nbsp;   """)

&nbsp;   

&nbsp;   # 同步Redis

&nbsp;   expired = db.query("SELECT coupon\_code FROM user\_coupon WHERE status=2 AND update\_time > NOW() - INTERVAL 1 DAY")

&nbsp;   for coupon in expired:

&nbsp;       redis.set(f"coupon:code:{coupon\['coupon\_code']}", "2")  # 2:已过期

追问点：



如果用户领券后没使用，券过期了怎么办？

（答：定时任务过期，Redis标记）



如果用户退款，优惠券怎么处理？

（答：根据业务规则，可能退还优惠券，要保证幂等）



第七部分：反问环节 (3分钟)

问题9：你有什么想问我的吗？

考察意图：



对公司和业务的兴趣



职业规划匹配度



思考深度



评分标准：



好：问出有深度的问题（未来技术方向、公司技术挑战、团队文化）



中：问常规问题



差：没问题



推荐问题（三面专属）：



技术战略相关：



公司未来3年的技术战略方向是什么？



咱们部门当前最大的技术挑战是什么？



公司在技术投入上的态度是怎样的？



团队与文化：



团队的技术氛围是什么样的？怎么保证代码质量？



团队的技术分享机制是怎样的？



怎么培养新人和技术骨干？



业务与挑战：



咱们业务目前最大的增长瓶颈在哪？



接下来半年最重要的项目是什么？



我在这个岗位上能解决什么核心问题？



个人发展：



这个岗位的晋升路径是怎样的？



公司支持技术深度发展还是管理路线？



有什么学习资源和培训机制？



面试反馈：



根据今天的交流，您觉得我哪些方面还需要提升？



如果有幸加入，您建议我重点在哪些方面加强？



面试评分总表

考察维度	权重	评分项	得分(1-10)	备注

架构设计能力	20%	订单中心架构		

优惠券系统设计		

复杂问题排查	15%	线上故障排查		

数据导出方案		

技术前瞻性	15%	架构演进规划		

技术领导力	15%	新人培养		

冲突解决		

价值观与软技能	10%	失败经历反思		

业务理解	10%	优惠券业务建模		

沟通协作	10%	跨团队协作案例		

反问环节	5%	问题质量		

总分	100%			

评级标准：



90-100分：卓越，技术专家/架构师潜质，可独立负责核心系统



80-89分：优秀，技术深度和广度兼备，能带小团队



70-79分：良好，技术扎实，需要加强架构和领导力



60-69分：及格，基础不错，但深度不够



60分以下：待定，建议加面或挂



以上是完整的Python工程师技术三面详细文档，包含：



9个核心问题（每题都有考察意图、评分标准、详细参考答案）



涵盖复杂业务建模、架构演进、技术领导力、价值观、业务理解



每题包含追问点和文档出处



完整的面试流程和时间分配



面试评分表

