package com.freedom.distributedlock.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.*;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * @author freedom
 * @date 2022/12/4 21:10
 */
public class ZkDistributedLock implements Lock {

    public static final String SEPARATOR = "-";

    private ZooKeeper zooKeeper;

    private String lockName;

    private String currentPath;

    private static final ThreadLocal<Integer> THREAD_LOCAL = new ThreadLocal<>();

    public ZkDistributedLock(ZooKeeper zooKeeper, String lockName) {
        this.zooKeeper = zooKeeper;
        this.lockName = lockName;
    }

    @Override
    public void lock() {
        // 如果有锁，直接重入
        final Integer flag = THREAD_LOCAL.get();
        if (Objects.nonNull(flag) && flag > 0) {
            THREAD_LOCAL.set(flag + 1);
            return;
        }

        try {
            currentPath = zooKeeper.create(ZkClient.ROOT_PATH + "/" + lockName + SEPARATOR, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

            String preNode = getPreNode(currentPath);

            // 如果前置节点为空，则获取锁成功，否则监听前置节点
            if (StringUtils.isBlank(preNode)) {
                THREAD_LOCAL.set(1);
                return;
            }

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            // 因为获取前置节点不具有原子性，再次判断zk中的前置节点是否存在
            if (zooKeeper.exists(ZkClient.ROOT_PATH + "/" + preNode, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    countDownLatch.countDown();
                }
            }) == null) {
                THREAD_LOCAL.set(1);
                return;
            }

            countDownLatch.await();

            THREAD_LOCAL.set(1);
        } catch (Exception e) {
            throw new RuntimeException("加锁失败", e);
        }
    }

    private String getPreNode(String currentPath) {
        final String curSerial = StringUtils.substringAfter(currentPath, SEPARATOR);

        final List<String> nodes;
        try {
            nodes = zooKeeper.getChildren(ZkClient.ROOT_PATH, false)
                    .stream().filter(node -> StringUtils.startsWith(node, lockName + SEPARATOR)).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("获取子节点失败", e);
        }

        if (CollectionUtils.isEmpty(nodes)) {
            throw new IllegalMonitorStateException("未找到子节点");
        }

        Collections.sort(nodes);

        final int index = Collections.binarySearch(nodes, curSerial);
        if (index > 0) {
            return nodes.get(index - 1);
        }
        return null;
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
        try {
            THREAD_LOCAL.set(THREAD_LOCAL.get() - 1);
            if (THREAD_LOCAL.get() == 0) {
                zooKeeper.delete(currentPath, 0);
            }
        } catch (Exception e) {
            throw new RuntimeException("解锁失败", e);
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }
}
