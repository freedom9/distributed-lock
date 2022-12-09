package com.freedom.distributedlock.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Objects;

/**
 * @author freedom
 * @date 2022/12/4 20:57
 */
@Slf4j
@Component
public class ZkClient {

    public static final String ROOT_PATH = "/distributed";

    @Value("${zk.host}")
    private String zkHost;

    @Getter
    private ZooKeeper zooKeeper;

    @PostConstruct
    public void init() {
        try {
            zooKeeper = new ZooKeeper(zkHost, 30000, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    log.info("获取链接成功");
                }
            });

            if (zooKeeper.exists(ROOT_PATH, Boolean.FALSE) == null) {
                zooKeeper.create(ROOT_PATH, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
            throw new RuntimeException("获取链接失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (Objects.isNull(zooKeeper)) {
                zooKeeper.close();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("关闭链接失败", e);
        }
    }
}
