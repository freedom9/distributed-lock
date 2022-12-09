package com.freedom.distributedlock.utils;

import org.apache.zookeeper.ZKUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * @author freedom
 * @date 2022/11/22 21:06
 */
@Component
public class DistributedLockFactory {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ZkClient zkClient;

    public RedisDistributedLock getRedisLock(String lockName) {
        return new RedisDistributedLock(redisTemplate, lockName);
    }

    public ZkDistributedLock getZkLock(String lockName) {
        return new ZkDistributedLock(zkClient.getZooKeeper(), lockName);
    }
}
