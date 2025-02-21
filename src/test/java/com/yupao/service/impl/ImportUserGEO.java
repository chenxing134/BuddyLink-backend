package com.yupao.service.impl;


import com.yupao.contant.RedisConstant;
import com.yupao.model.domain.User;
import com.yupao.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class ImportUserGEO {
    @Resource
    RedisTemplate redisTemplate;

    @Resource
    UserService userService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private List<User> keyUsersList;

    @PostConstruct
    public void initialUserList() {
         this.keyUsersList = userService.list();
         if (this.keyUsersList.isEmpty()) {
             System.out.println("keyUsersList is empty");
         }
    }

    /**
     * 将所有用户的经纬度导入 Redis
     */
    @Test
    public void importUserGEOByRedis() {
        List<User> userList = userService.list();
        String key = RedisConstant.USER_GEO_KEY;
        List<RedisGeoCommands.GeoLocation<String>> locationList = new ArrayList<>(userList.size());
        for (User user : userList) {
            // 往locationList添加每个用户的经纬度数据
            locationList.add(new RedisGeoCommands.GeoLocation<>(String.valueOf(user.getId()), new Point(user.getLongitude(),
                    user.getDimension())));
        }
        // 将每个用户的经纬度信息写入Redis中
        stringRedisTemplate.opsForGeo().add(key, locationList);
    }

    /**
     * 获取 id 为 1 的用户与所有用户的距离
     */
    @Test
    public void getUserGeo() {
        String key = RedisConstant.USER_GEO_KEY;
        List<User> userList = userService.list();

        // 计算每个用户与登录用户的距离
        for (User user : userList) {
            Distance distance = stringRedisTemplate.opsForGeo().distance(key,
                    "1", String.valueOf(user.getId()), RedisGeoCommands.DistanceUnit.KILOMETERS);
            System.out.println("User: " + user.getId() + ", Distance: " +
                    distance.getValue() + " " + distance.getUnit());
        }
    }

    /**
     * 搜索距离 id 为 1 的 1500 km 的用户
     */
    @Test
    public void searchUserByGeo() {
        User loginUser = userService.getById(1);
        Distance geoRadius = new Distance(1500, RedisGeoCommands.DistanceUnit.KILOMETERS);
        Circle circle  = new Circle(new Point(loginUser.getLongitude(), loginUser.getDimension()), geoRadius);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(RedisConstant.USER_GEO_KEY, circle);
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
            if (!result.getContent().getName().equals("1")) {
                System.out.println(result.getContent().getName());
            }
        }
    }
}