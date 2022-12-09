package com.freedom.distributedlock.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @author freedom
 * @date 2022/11/22 20:39
 */
public class RedisDistributedLock implements Lock {

    private final StringRedisTemplate redisTemplate;

    private final String lockName;

    private final String uuid;

    private final Integer expire = 30;

    public RedisDistributedLock(StringRedisTemplate redisTemplate, String lockName) {
        this.redisTemplate = redisTemplate;
        this.lockName = lockName;
        this.uuid = UUID.randomUUID() + ":" + Thread.currentThread().getId();
    }


    @Override
    public void lock() {
        final String script = "if redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1 " +
                "then " +
                "    redis.call('hincrby', KEYS[1], ARGV[1], 1) " +
                "    redis.call('expire', KEYS[1], ARGV[2]) " +
                "    return 1 " +
                "else " +
                "    return 0 " +
                "end ";
        while (!redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Collections.singletonList(lockName), uuid, expire.toString())) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        renewExpire();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {
        final String script = "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 " +
                "then " +
                "    return nil " +
                "elseif redis.call('hincrby', KEYS[1], ARGV[1], -1) == 0 " +
                "then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end ";
        final Long flag = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Collections.singletonList(lockName), uuid);
        if (Objects.isNull(flag)) {
            throw new IllegalMonitorStateException();
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }

    /**
     * 自动续期
     */
    private void renewExpire() {
        final String script = "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 " +
                "then " +
                "    return redis.call('expire', KEYS[1], 30) " +
                "else " +
                "    return 0 " +
                "end ";
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Collections.singletonList(lockName), uuid)) {;
                    renewExpire();
                }
            }
        }, expire * 1000 / 3);
    }
}
