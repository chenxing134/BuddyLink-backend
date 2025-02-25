package com.yupao.service.impl;

import com.yupao.model.domain.User;
import com.yupao.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;


/**
 * 导入用户测试类，包含批量插入用户和并发批量插入用户的测试方法。
 */
@SpringBootTest
public class InsertUsersTest {

    @Resource
    private UserService userService;

    /**
     * 创建一个线程池用于并发操作。线程池的核心线程数为40，最大线程数为1000，
     * 线程空闲时间为10000分钟，队列容量为10000。
     */
    private ExecutorService executorService = new ThreadPoolExecutor(
        40,
        1000,
        10000,
        TimeUnit.MINUTES,
        new ArrayBlockingQueue<>(10000)
    );

    /**
     * 批量插入用户测试方法。
     * 使用StopWatch记录执行时间，并创建指定数量的用户对象后调用userService.saveBatch进行批量保存。
     */
    @Test
    public void doInsertUsers() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        final int INSERT_NUM = 100000; // 定义要插入的用户数量
        List<User> userList = new ArrayList<>();

        for (int i = 0; i < INSERT_NUM; i++) {
            User user = createUser("辰星", "Chenxing");
            userList.add(user);
        }

        // 调用批量保存方法，每批次保存10000条记录
        userService.saveBatch(userList, 10000);

        stopWatch.stop();
        System.out.println("批量插入用户耗时: " + stopWatch.getTotalTimeMillis() + " 毫秒");
    }

    /**
     * 并发批量插入用户测试方法。
     * 将用户数据分成多个批次，并发地调用userService.saveBatch进行批量保存。
     */
    @Test
    public void doConcurrencyInsertUsers() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        int batchSize = 5000; // 每个批次的用户数量
        int j = 0;
        List<CompletableFuture<Void>> futureList = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            List<User> userList = new ArrayList<>();
            while (true) {
                j++;
                User user = createUser("假辰星", "fakechenxing");
                userList.add(user);

                if (j % batchSize == 0) {
                    break;
                }
            }

            // 异步执行批量保存任务
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                System.out.println("线程名称: " + Thread.currentThread().getName());
                userService.saveBatch(userList, batchSize);
            }, executorService);

            futureList.add(future);
        }

        // 等待所有异步任务完成
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).join();

        stopWatch.stop();
        System.out.println("并发批量插入用户耗时: " + stopWatch.getTotalTimeMillis() + " 毫秒");
    }

    /**
     * 辅助方法：创建并返回一个User对象，简化用户信息设置过程。
     *
     * @param username 用户名
     * @param userAccount 用户账号
     * @return 创建的User对象
     */
    private User createUser(String username, String userAccount) {
        User user = new User();
        user.setUsername(username);
        user.setUserAccount(userAccount);
        user.setAvatarUrl("https://636f-codenav-8grj8px727565176-1256524210.tcb.qcloud.la/img/logo.png");
        user.setGender(0);
        user.setUserPassword("12345678");
        user.setPhone("123");
        user.setEmail("123@qq.com");
        user.setTags("[]");
        user.setUserStatus(0);
        user.setUserRole(0);
        user.setPlanetCode("11111111");
        return user;
    }
}

