package com.yupao.service;

import com.yupao.model.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupao.model.request.UserEditRequest;
import com.yupao.model.request.UserRegisterRequest;
import com.yupao.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户服务
 *
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @return 新用户 id
     */
    long userRegister(HttpServletRequest request, UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户脱敏
     *
     * @param originUser
     * @return
     */
    User getSafetyUser(User originUser);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    int userLogout(HttpServletRequest request);

    /**
     * 根据标签搜索用户
     * @param tagNameList
     * @param request
     * @return
     */
    List<UserVO> searchUsersByTags(List<String> tagNameList, HttpServletRequest request);

    List<User> searchUserByTagsBySQL(List<String> tagNameList);

    /**
     *
     * @param userEditRequest
     * @param loginUser
     * @return
     */
    int updateUser(UserEditRequest userEditRequest, User loginUser);

    /**
     *获取当前登录用户信息
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    boolean isAdmin(HttpServletRequest request);

    /**
     * 是否为管理员
     * @param loginUser
     * @return
     */
    boolean isAdmin(User loginUser);

    /**
     *
     * @param pageSize
     * @param pageNum
     * @param request
     * @return
     */
    List<UserVO> recommendUsers(long pageSize, long pageNum, HttpServletRequest request);

    /**
     * 匹配用户
     * @param num
     * @param loginUser
     * @return
     */
    List<UserVO> matchUsers(long num, User loginUser );

    /**
     *
     * @param radius
     * @param loginUser
     * @return
     */
    List<UserVO> searchNearby(int radius, User loginUser);
}
