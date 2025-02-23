package com.yupao.service.impl;

import cn.hutool.json.JSONUtil;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yupao.common.ErrorCode;
import com.yupao.contant.RedisConstant;
import com.yupao.exception.BusinessException;
import com.yupao.manager.RedisBloomFilter;
import com.yupao.manager.RedisLimiterManager;
import com.yupao.model.domain.User;
import com.yupao.model.request.UserEditRequest;
import com.yupao.model.request.UserRegisterRequest;
import com.yupao.model.vo.UserVO;
import com.yupao.service.UserService;
import com.yupao.mapper.UserMapper;
import com.yupao.utils.AlgorithmUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.beans.BeanUtils;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yupao.contant.UserConstant.ADMIN_ROLE;
import static com.yupao.contant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现类
 *
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private Retryer<Boolean> retryer;

    @Resource
    StringRedisTemplate stringRedisTemplate;



    @Resource
    private RedisBloomFilter redisBloomFilter;

    @Resource
    private RedisLimiterManager redisLimiterManager;


    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "chenxing";
    // 表示 Redis 是否有数据
    private boolean redisHasData = false;
    /**
     * 用户注册
     *
     * @return 新用户 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long userRegister(HttpServletRequest request, UserRegisterRequest userRegisterRequest) {
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        List<String> tagNameList = userRegisterRequest.getTagNameList();
        String username = userRegisterRequest.getUsername();
        Double longitude = userRegisterRequest.getLongitude();
        Double dimension = userRegisterRequest.getDimension();
        String ip = request.getRemoteHost();
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请至少选择一个标签");
        }
        if (StringUtils.isBlank(username) || username.length() > 10) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称不合法，长度不得超过 10");
        }
        if (longitude == null || longitude > 180 || longitude < -180) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "坐标经度不合法");
        }
        if (dimension == null || dimension > 90 || dimension < -90) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "坐标纬度不合法");
        }
        // 限流
        redisLimiterManager.doRateLimiter(RedisConstant.REDIS_LIMITER_REGISTER + ip, 2, 1);
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            return -1;
        }
        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已注册");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 3. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setLongitude(longitude);
        user.setDimension(dimension);
        user.setUsername(username);
        // 处理用户标签
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('[');
        for (int i = 0; i < tagNameList.size(); i++) {
            stringBuilder.append('"').append(tagNameList.get(i)).append('"');
            if (i < tagNameList.size() - 1) {
                stringBuilder.append(',');
            }
        }
        stringBuilder.append(']');
        user.setTags(stringBuilder.toString());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "添加失败");
        }
        // 如果用户信息插入数据库，则计算用户坐标信息并存入Redis
        Long addToRedisResult = stringRedisTemplate.opsForGeo().add(RedisConstant.USER_GEO_KEY,
                new Point(user.getLongitude(), user.getDimension()), String.valueOf(user.getId()));
        if (addToRedisResult == null || addToRedisResult <= 0) {
            log.error("用户注册时坐标信息存入Redis失败");
        }
        long userId = user.getId();
        // 添加至用户布隆过滤器
        redisBloomFilter.addUserToFilter(userId);
        // 删除用户缓存
        Set<String> keys = stringRedisTemplate.keys(RedisConstant.USER_RECOMMEND_KEY + ":*");
        for (String key : keys) {
            try {
                retryer.call(() -> stringRedisTemplate.delete(key));
            } catch (ExecutionException e) {
                log.error("用户注册后删除缓存重试时失败");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            } catch (RetryException e) {
                log.error("用户注册后删除缓存达到最大重试次数或超过时间限制");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }
        return user.getId();
    }


    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return null;
        }
        if (userAccount.length() < 4) {
            return null;
        }
        if (userPassword.length() < 8) {
            return null;
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            return null;
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            return null;
        }
        // 3. 用户脱敏
        User safetyUser = getSafetyUser(user);
        // 4. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, safetyUser);
        return safetyUser;
    }

    /**
     * 用户脱敏
     *
     * @param originUser
     * @return
     */
    @Override
    public User getSafetyUser(User originUser) {
        if (originUser == null) {
            return null;
        }
        User safetyUser = new User();
        safetyUser.setId(originUser.getId());
        safetyUser.setUsername(originUser.getUsername());
        safetyUser.setUserAccount(originUser.getUserAccount());
        safetyUser.setAvatarUrl(originUser.getAvatarUrl());
        safetyUser.setGender(originUser.getGender());
        safetyUser.setPhone(originUser.getPhone());
        safetyUser.setEmail(originUser.getEmail());
        safetyUser.setUserRole(originUser.getUserRole());
        safetyUser.setUserStatus(originUser.getUserStatus());
        safetyUser.setCreateTime(originUser.getCreateTime());
        safetyUser.setProfile(originUser.getProfile());
        safetyUser.setTags(originUser.getTags());
        safetyUser.setLongitude(originUser.getLongitude());
        safetyUser.setDimension(originUser.getDimension());
        return safetyUser;
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public int userLogout(HttpServletRequest request) {
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return 1;
    }

    /**
     *   根据标签搜索用户。（内存过滤）
     * @param tagNameList  用户要搜索的标签
     * @return
     */
    @Override
    public List<User> searchUserByTags(List<String> tagNameList){
        if (CollectionUtils.isEmpty(tagNameList)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //1.先查询所有用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        List<User> userList = userMapper.selectList(queryWrapper);
        Gson gson = new Gson();
        //2.判断内存中是否包含要求的标签
        return userList.stream().filter(user -> {
            String tagStr = user.getTags();
            if (StringUtils.isBlank(tagStr)){
                return false;
            }

            Set<String> tempTagNameSet = gson.fromJson(tagStr,new TypeToken<Set<String>>(){}.getType());
            tempTagNameSet = Optional.ofNullable(tempTagNameSet).orElse(new HashSet<>());
            for (String tagName : tagNameList){
                if (!tempTagNameSet.contains(tagName)){
                    return false;
                }
            }
            return true;
        }).map(this::getSafetyUser).collect(Collectors.toList());
    }
    /**
     * 根据标签搜索用户（SQL）
     *
     * @param tagNameList 用户要拥有标签
     * @return
     */
    @Override
    public List<User> searchUserByTagsBySQL(List<String> tagNameList) {
        if(CollectionUtils.isEmpty(tagNameList)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        //拼接 and 查询
        for (String tagName : tagNameList) {
            queryWrapper = queryWrapper.like("tags", tagName);
        }
        List<User> userList = userMapper.selectList(queryWrapper);
        return userList.stream().map(this::getSafetyUser).collect(Collectors.toList());
    }

    @Override
    public int updateUser(UserEditRequest userEditRequest, User loginUser) {
        long userId = userEditRequest.getId();
        // 如果是管理员允许更新任意用户信息
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (this.getById(userId) == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "用户不存在");
        }
        // todo 补充更多校验，如果用户传的值只有id，没有其它参数则不执行更新操作
        // 如果是管理员，允许更新任意用户信息，只允许更新当前用户信息
        if (!isAdmin(loginUser) && userId != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        User oldUser = this.baseMapper.selectById(userId);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "用户不存在");
        }
        Double longitude = userEditRequest.getLongitude();
        Double dimension = userEditRequest.getDimension();
        if (longitude != null && (longitude > 180 || longitude < -180)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "坐标经度不合法");
        }
        if (dimension != null && (dimension > 90 || dimension < -90)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "坐标纬度不合法");
        }
        if (longitude != null) {
            stringRedisTemplate.opsForGeo().add(RedisConstant.USER_GEO_KEY, new Point(longitude, oldUser.getDimension()),
                    String.valueOf(userId));
        }
        if (dimension != null) {
            stringRedisTemplate.opsForGeo().add(RedisConstant.USER_GEO_KEY, new Point(oldUser.getDimension(), dimension),
                    String.valueOf(userId));
        }
        User user = new User();
        BeanUtils.copyProperties(userEditRequest, user);
        List<String> tags = userEditRequest.getTags();
        if (!CollectionUtils.isEmpty(tags)) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append('[');
            for (int i = 0; i < tags.size(); i++) {
                stringBuilder.append('"').append(tags.get(i)).append('"');
                if (i < tags.size() - 1) {
                    stringBuilder.append(',');
                }
            }
            stringBuilder.append(']');
            user.setTags(stringBuilder.toString());
        }
        int i = this.baseMapper.updateById(user);
        if (i > 0) {
            Set<String> keys = stringRedisTemplate.keys(RedisConstant.USER_RECOMMEND_KEY + ":*");
            for (String key : keys) {
                try {
                    retryer.call(() -> stringRedisTemplate.delete(key));
                } catch (ExecutionException e) {
                    log.error("用户修改信息后删除缓存重试时失败");
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                } catch (RetryException e) {
                    log.error("用户修改信息后删除缓存达到最大重试次数或超过时间限制");
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }
            }
        }
        return i;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);

        if (userObj == null) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return (User) userObj;
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return user != null && user.getUserRole() == ADMIN_ROLE;
    }

    /**
     * 是否为管理员
     *
     * @param loginUser
     * @return
     */
    @Override
    public boolean isAdmin(User loginUser) {
        // 仅管理员可查询
        return loginUser != null && loginUser.getUserRole() == ADMIN_ROLE;
    }

    @Override
    public synchronized List<UserVO> recommendUsers(long pageSize, long pageNum, HttpServletRequest request) {
        User loginUser = this.getLoginUser(request);
        String redisKey = RedisConstant.USER_RECOMMEND_KEY + ":" + loginUser.getId();
        // 如果缓存中有数据，直接读缓存
        long start = (pageNum - 1) * pageSize;
        long end = start + pageSize - 1;
        List<String> userVOJsonListRedis = stringRedisTemplate.opsForList().range(redisKey, start, end);
        // 将查询的缓存反序列化为 User 对象
        List<UserVO> userVOList = new ArrayList<>();
        userVOList = userVOJsonListRedis.stream()
                .map(UserServiceImpl::transferToUserVO).collect(Collectors.toList());
        // 判断 Redis 中是否有数据
        redisHasData = !CollectionUtils.isEmpty(stringRedisTemplate.opsForList().range(redisKey, 0, -1));
        if (!CollectionUtils.isEmpty(userVOJsonListRedis)) {
            return userVOList;
        }
        // 缓存无数据再走数据库
        if (!redisHasData) {
            // 无缓存，查询数据库，并将数据写入缓存
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.ne("id", loginUser.getId());
            List<User> userList = this.list(queryWrapper);

            String redisUserGeoKey = RedisConstant.USER_GEO_KEY;

            // 将User转换为UserVO，在进行序列化
            userVOList = userList.stream()
                    .map(user -> {
                        // 查询距离
                        Distance distance = stringRedisTemplate.opsForGeo().distance(redisUserGeoKey,
                                String.valueOf(loginUser.getId()), String.valueOf(user.getId()),
                                RedisGeoCommands.DistanceUnit.KILOMETERS);
                        Double value = distance.getValue();
                        // 创建UserVO对象并设置属性
                        UserVO userVO = new UserVO();
                        userVO.setId(user.getId());
                        userVO.setUsername(user.getUsername());
                        userVO.setUserAccount(user.getUserAccount());
                        userVO.setAvatarUrl(user.getAvatarUrl());
                        userVO.setGender(user.getGender());
                        userVO.setProfile(user.getProfile());
                        userVO.setPhone(user.getPhone());
                        userVO.setEmail(user.getEmail());
                        userVO.setUserStatus(user.getUserStatus());
                        userVO.setCreateTime(user.getCreateTime());
                        userVO.setUpdateTime(user.getUpdateTime());
                        userVO.setUserRole(user.getUserRole());
                        userVO.setTags(user.getTags());
                        if (value != null) {
                            userVO.setDistance(value); // 设置距离值
                        } else {
                            userVO.setDistance(0.0);
                        }
                        return userVO;
                    })
                    .collect(Collectors.toList());
            // 将序列化的 List 写入缓存
            List<String> userVOJsonList = userVOList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
            try {
                stringRedisTemplate.opsForList().rightPushAll(redisKey, userVOJsonList);
                stringRedisTemplate.expire(redisKey, 10, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("redis set key error", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缓存写入失败");
            }
        }
        userVOList = stringRedisTemplate.opsForList().range(redisKey, start, end)
                .stream().map(UserServiceImpl::transferToUserVO).collect(Collectors.toList());
        return userVOList;
    }

    @Override
    public List<UserVO> matchUsers(long num, User loginUser) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNotNull("tags");
        queryWrapper.ne("id", loginUser.getId());
        queryWrapper.select("id", "tags");
        List<User> userList = this.list(queryWrapper);

        String tags = loginUser.getTags();
        Gson gson = new Gson();
        List<String> tagList = gson.fromJson(tags, new TypeToken<List<String>>() {
        }.getType());
        // 用户列表的下表 => 相似度'
        List<Pair<User, Long>> list = new ArrayList<>();
        // 依次计算当前用户和所有用户的相似度
        for (User user : userList) {
            String userTags = user.getTags();
            //无标签的 或当前用户为自己
            if (StringUtils.isBlank(userTags) || user.getId() == loginUser.getId()) {
                continue;
            }
            List<String> userTagList = gson.fromJson(userTags, new TypeToken<List<String>>() {
            }.getType());
            //计算分数
            long distance = AlgorithmUtils.minDistance(tagList, userTagList);
            list.add(new Pair<>(user, distance));
        }
        //按编辑距离有小到大排序
        List<Pair<User, Long>> topUserPairList = list.stream()
                .sorted((a, b) -> (int) (a.getValue() - b.getValue()))
                .limit(num)
                .collect(Collectors.toList());
        //有顺序的userID列表
        List<Long> userListVo = topUserPairList.stream().map(pari -> pari.getKey().getId()).collect(Collectors.toList());

        //根据id查询user完整信息
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.in("id", userListVo);
        Map<Long, List<User>> userIdUserListMap = this.list(userQueryWrapper).stream()
                .map(this::getSafetyUser)
                .collect(Collectors.groupingBy(User::getId));

        List<User> finalUserList = new ArrayList<>();
        for (Long userId : userListVo) {
            finalUserList.add(userIdUserListMap.get(userId).get(0));
        }

        String redisUserGeoKey = RedisConstant.USER_GEO_KEY;
        List<UserVO> finalUserVOList = finalUserList.stream().map(user -> {
            Distance distance = stringRedisTemplate.opsForGeo().distance(redisUserGeoKey, String.valueOf(loginUser.getId()),
                    String.valueOf(user.getId()), RedisGeoCommands.DistanceUnit.KILOMETERS);

            UserVO userVO = new UserVO();
            userVO.setId(user.getId());
            userVO.setUsername(user.getUsername());
            userVO.setUserAccount(user.getUserAccount());
            userVO.setAvatarUrl(user.getAvatarUrl());
            userVO.setGender(user.getGender());
            userVO.setProfile(user.getProfile());
            userVO.setPhone(user.getPhone());
            userVO.setEmail(user.getEmail());
            userVO.setUserStatus(user.getUserStatus());
            userVO.setCreateTime(user.getCreateTime());
            userVO.setUpdateTime(user.getUpdateTime());
            userVO.setUserRole(user.getUserRole());
            userVO.setTags(user.getTags());
            userVO.setDistance(distance.getValue());
            return userVO;

        }).collect(Collectors.toList());
        return finalUserVOList;
    }

    @Override
    public List<UserVO> searchNearby(int radius, User loginUser) {
        String geoKey = RedisConstant.USER_GEO_KEY;
        String userId = String.valueOf(loginUser.getId());
        Double longitude = loginUser.getLongitude();
        Double dimension = loginUser.getDimension();
        if (longitude == null || dimension == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "登录用户经纬度参数为空");
        }
        Distance geoRadius = new Distance(radius, RedisGeoCommands.DistanceUnit.KILOMETERS);
        Circle circle = new Circle(new Point(longitude, dimension), geoRadius);
        // 在Redis中根据地理区域查找附近的用户
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(geoKey, circle);
        List<Long> userIdList = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
            String id = result.getContent().getName();
            if (!userId.equals(id)) {
                userIdList.add(Long.parseLong(id));
            }
        }
        //计算计算每个用户与登录用户的距离
        List<UserVO> userVOList = userIdList.stream().map(
                id -> {
                    UserVO userVO = new UserVO();
                    User user = this.getById(id);
                    BeanUtils.copyProperties(user, userVO);
                    Distance distance = stringRedisTemplate.opsForGeo().distance(geoKey, userId, String.valueOf(id),
                            RedisGeoCommands.DistanceUnit.KILOMETERS);
                    userVO.setDistance(distance.getValue());
                    return userVO;
                }
        ).collect(Collectors.toList());
        return userVOList;
    }

    private static UserVO transferToUserVO(String userVOJson) {
        return JSONUtil.toBean(userVOJson, UserVO.class);
    }

}

