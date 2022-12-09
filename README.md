#### 1、MySQL锁
##### 1.1 导致JVM本地锁的三种情况
1. **多例模式**
2. **事务注解@Transactional**
``` java
@Transactional
public synchronized void test() {
    // 步骤一
    // 步骤二
    // 步骤三
}
```
此时synchronized不起作用，因为事务自动提交/回退比锁释放后执行。

3. **服务集群部署**

##### 1.2 一条sql语句，更新数量判断
```sql
update test set count = count - 1 where product_code = '10001' and count >= 1
```

**解决**：锁失效的情况。
**问题**：
1. 锁范围问题，表级锁或行级锁
2. 同一个商品有多条库存记录
3. 无法记录库存变化前后的状态

##### 1.3 悲观锁：select... for update
**解决**：一条sql的问题。
**问题**：
1. 性能下降
2. 死锁问题：对多条数据加锁时，加锁顺序要一致
3. 库存操作要统一，不能有点地方使用悲观锁，有些地方使用普通select

##### 1.4 乐观锁：时间戳 version版本号 CAS机制
**解决**：悲观锁问题。
**问题**：
1. 高并发情况下，性能极低
2. ABA问题
3. 读写分离情况下导致乐观锁不可靠

##### 1.5 MySQL锁总结
1. 性能：一个sql > 悲观锁 > jvm锁 > 乐观锁
2. 如果追求极致性能、业务场景简单并且不需要记录数据前后变化的情况下。优先选择：==一个sql==
3. 如果写并发量较低（多读），争抢不是很激烈的情况下。优先选择：==乐观锁==
4. 如果写并发量较高，一般会经常冲突，此时选择乐观锁的话，会导致业务代码不间断的重试。优先选择：==悲观锁==
5. ==不推荐jvm本地锁==

#### 2、Redis锁
##### 2.1 lua脚本
```lua
EVAL script nummkeys key [key ...] arg [arg ...]
```
>* script：lua脚本字符串。
>* mumkeys：lua脚本KEYS数组的大小。
>* key [key ...]：KEYS数组中的元素，下标从1开始。
>* arg [arg ...]：ARGV数组中的元素。

##### 2.2 ReentrantLock加解锁流程
**2.2.1 加锁：ReentrantLock.lock() -> NonfairSync.lock() -> AQS.acquire(1) -> NofairSync.tryAcquire(1) -> Sync.nonfairTryAcquire()**
1. CAS获取锁，如果没有线程占用锁（state==0），加锁成功并记录当前线程是有锁线程（两次判断）
2. 如果state的值不为0，说明锁已经被占用。则判断当前线程是否是有锁线程，如果是则重入（state + 1）
3. 否则加锁失败，入队等待

**2.2.2 解锁：ReentrantLock.unlock() -> AQS.release(1) -> Sync.tryRelease(1)**
1. 判断当前线程是否是有锁线程，不是则抛出异常
2. 对state的值减一之后，判断state的值是否为0，为0则解锁成功，返回true
3. 如果减1后的值不为0，则返回false

##### 2.3 手写分布式锁小结
**2.3.1 加锁**
1. setnx：独占排它，但是会导致死锁、不可重入。
2. set k v ex 30 nx：独占排它，但是不可重入还是没解决。
3. hash + lua脚本：可重入锁（参考ReentrantLock的非公平可重入锁）。
>* 判断锁是否被占用（exists），如果没有被占用则直接获取锁（hset/hincrby）并设置过期事件（expire）。
>* 判断锁是否被占用，则判断是否当前线程占用，如果是则重入（hincrby）并重置过期时间（expire）。
>* 否则获取锁失败，将在代码中重试。
```lua
if redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], AEGV[1]) == 1
then
    redis.call('hincrby', KEYS[1], ARGV[1], 1)
    redis.call('expire', KEYS[1], ARGV[2])
    return 1
else
    return 0
end
```
4. Timer定时器 + lua脚本：实现锁的自动续期。
```lua
if redis.call('hexists', KEYS[1], ARGV[1]) == 1
then
    return redis.call('expire', KEYS[1], 30)
else
    return 0
end
```

**2.3.2 解锁**
1. del：导致误删。
2. lua脚本：先判断在删除同时保证原子性。
```lua
if redis.call('get', KEYS[1]) == ARGV[1]
then
    return redis.call('del', KEYS[1])
else
    return 0
end
```
3. hash + lua脚本：可重入锁
>* 判断当前线程的锁是否存在，不存在则返回nil，将抛出异常。
>* 存在则直接减1（hincrby），判断减1后的值是否为0，为0则释放锁（del），并返回1。
>* 不为0，则返回0。
```lua
if redis.call('hexists', KEYS[1], ARGV[1]) == 0
then
    return nil
elseif redis.call('hincrby', KEYS[1], ARGV[1], -1) == 0
then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

##### 2.4 redisTemplate.execute()调用脚本
execute(RedisScript<T> script, List<K> keys, Object... args)中的RedisScript使用两个参数 DefaultRedisScript(String script, @Nullable Class<T> resultType)。==因为resultType不填写，会出现未知错误==。

##### 2.5 Redisson分布式锁
[分布式锁和同步器](https://github.com/redisson/redisson/wiki/8.-%E5%88%86%E5%B8%83%E5%BC%8F%E9%94%81%E5%92%8C%E5%90%8C%E6%AD%A5%E5%99%A8)

#### 3、Zookeeper锁
##### 3.1 分布式锁小结
1. 互斥 排他：zk节点的不可重复性，已经序列化节点的有序性
2. 防死锁
>* 可自动释放锁：临时有序节点
>* 可重入锁：借助于ThreadLocal
3. 防误删：临时节点
4. 加锁、解锁具有原子性
5. 单点问题：zk一般是集群部署
6. 集群问题：zk集群是强一致性，只要集群中有半数以上的机器存活，就可对外提供服务
7. 公平锁：有序性节点
