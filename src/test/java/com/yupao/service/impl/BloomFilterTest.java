package com.yupao.service.impl;


import com.yupao.manager.RedisBloomFilter;
import com.yupao.model.domain.User;
import com.yupao.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
public class BloomFilterTest {

    @Resource
    private RedisBloomFilter redisBloomFilter;

    @Resource
    private UserService userService;

    @Test
    public void addAllUsersToFilter() {
        List<Long> ids = userService.list().stream().map(User::getId).collect(Collectors.toList());
        for (long id : ids) {
            redisBloomFilter.addUserToFilter(id);
        }

    }

}
