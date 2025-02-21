package com.yupao.contant;

public interface RedisConstant {
    String SYSTEM_ID = "BuddyLink:team:join";
    String USER_JOIN_TEAM = "BuddyLink:team:join:";
    String USER_GEO_KEY = "BuddyLinking:user:geo";
    String USER_ADD_KEY = "BuddyLinking:user:add";
    String USER_RECOMMEND_KEY = "BuddyLink:user:recommend";

    String REDIS_LIMITER_REGISTER = "BuddyLink:limiter:register:";
    String USER_SIGNIN_KEY = "BuddyLink:user:signin:";
    String USER_BLOOM_FILTER_KEY = "BuddyLink:user:bloomfilter";
    /**
     * 用户推荐缓存
     */
    /**
     * 最小缓存随机时间
     */
    public static final int MINIMUM_CACHE_RANDOM_TIME = 2;
    /**
     * 最大缓存随机时间
     */
    public static final int MAXIMUM_CACHE_RANDOM_TIME = 3;
    /**
     * 缓存时间偏移
     */
    public static final int CACHE_TIME_OFFSET = 10;
}
