package com.freedom.distributedlock;

import com.freedom.distributedlock.utils.DistributedLockFactory;
import com.freedom.distributedlock.utils.RedisDistributedLock;
import com.freedom.distributedlock.utils.ZkDistributedLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DistributedLockApplicationTests {

    @Autowired
    private DistributedLockFactory distributedLockFactory;

    @Test
    void testRedisLock() {
        final RedisDistributedLock lock = distributedLockFactory.getRedisLock("lock");
        try {
            lock.lock();

            Thread.sleep(1000);

            lock.lock();

            Thread.sleep(50000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
            lock.unlock();
        }
    }

    @Test
    void testZkLock() {
        final ZkDistributedLock lock = distributedLockFactory.getZkLock("lock");
        try {
            lock.lock();

            Thread.sleep(1000);

            lock.lock();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
            lock.unlock();

            System.out.println("执行成功");
        }
    }

}
