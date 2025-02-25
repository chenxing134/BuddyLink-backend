package com.yupao.service.impl;

import com.yupao.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.annotation.Resource;

/**
 * Redis 测试
 *
 */
@SpringBootTest
public class RedisTest {

    @Resource
    private RedisTemplate redisTemplate;

    @Test
    void test() {
        ValueOperations valueOperations = redisTemplate.opsForValue();
        // 增
        valueOperations.set("chenxingString", "dog");
        valueOperations.set("chenxingInt", 1);
        valueOperations.set("chenxingDouble", 2.0);
        User user = new User();
        user.setId(1L);
        user.setUsername("chenxing");
        valueOperations.set("chenxingUser", user);
        // 查
        Object chenxing = valueOperations.get("chenxingString");
        Assertions.assertTrue("dog".equals((String) chenxing));
        chenxing = valueOperations.get("chenxingInt");
        Assertions.assertTrue(1 == (Integer) chenxing);
        chenxing = valueOperations.get("chenxingDouble");
        Assertions.assertTrue(2.0 == (Double) chenxing);
        System.out.println(valueOperations.get("chenxingUser"));
        valueOperations.set("chenxingString", "dog");
        redisTemplate.delete("chenxingString");
    }
}
