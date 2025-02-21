package com.yupao.manager;


import com.yupao.common.ErrorCode;
import com.yupao.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class RedisLimiterManager {

    @Resource
    private RedissonClient redissonClient;

    public void doRateLimiter(String key, long time, long frequency) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, time, frequency, RateIntervalUnit.MINUTES);
        boolean b = rateLimiter.tryAcquire();
        if (!b) {
            log.error("{}请求次数过多，请稍后重试", key);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST);
        }
    }
}
