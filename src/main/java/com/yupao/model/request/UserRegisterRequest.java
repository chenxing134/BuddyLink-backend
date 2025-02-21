package com.yupao.model.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户注册请求体
 *
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户密码
     */
    private String userPassword;

    /**
     * 校验密码
     */
    private String checkPassword;

    /**
     * 星球编号
     */
    private String planetCode;

    private Integer gender;

    private String avatarUrl;

    private String username;

    private String phone;

    private List<String> tagNameList;

    private Double longitude;

    private Double Dimension;
}
