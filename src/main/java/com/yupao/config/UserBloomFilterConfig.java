package com.yupao.config;


import com.yupao.contant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
@Slf4j
public class UserBloomFilterConfig {

    @Resource
    private RedissonClient redissonClient;

    @Bean("userBloomFilter")
    public RBloomFilter<Long> redisUserBloomFilter() {
        String userBloomFilterKey = RedisConstant.USER_BLOOM_FILTER_KEY;
        RBloomFilter<Long> userBloomFilter = redissonClient.getBloomFilter(userBloomFilterKey);
        if (!userBloomFilter.isExists()) {
            boolean b = userBloomFilter.tryInit(1000, 0.01);
            if (!b) {
                log.error("用户布隆过滤器：{} 初始化失败", userBloomFilterKey);
            }
        }
        return userBloomFilter;
    }
}
