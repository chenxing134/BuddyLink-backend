package com.yupao.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupao.model.domain.UserTeam;
import com.yupao.service.UserTeamService;
import com.yupao.mapper.UserTeamMapper;
import org.springframework.stereotype.Service;

/**
* @author 16013
* @description 针对表【user_team(用户队伍关系)】的数据库操作Service实现
* @createDate 2025-02-10 15:03:21
*/
@Service
public class UserTeamServiceImpl extends ServiceImpl<UserTeamMapper, UserTeam>
    implements UserTeamService{

}




