package com.yupao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupao.model.domain.Team;
import com.yupao.model.domain.User;
import com.yupao.model.dto.TeamQuery;
import com.yupao.model.request.TeamJoinRequest;
import com.yupao.model.request.TeamQuitRequest;
import com.yupao.model.request.TeamUpdateRequest;
import com.yupao.model.vo.TeamUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 16013
* @description 针对表【team(队伍)】的数据库操作Service
* @createDate 2025-02-10 15:05:44
*/
public interface TeamService extends IService<Team> {
    /**
     * 创建队伍
     * @param team
     * @param loginUser
     * @return
     */
    long addTeam(Team team, User loginUser);

    /**
     * 搜索队伍
     * @param teamQuery
     * @return
     */
    List<TeamUserVO> listTeams(TeamQuery teamQuery,boolean isAdmin);

    /**
     * 更新队伍
     * @param teamUpdateRequest
     * @param loginUser
     * @return
     */
    boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser);

    /**
     * 删除队伍
     * @param id
     * @param loginUser
     * @return
     */
    boolean deleteTeam(long id, User loginUser);

    /**
     * 加入队伍
     * @param teamJoinRequest
     * @param loginUser
     * @return
     */
    boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser);

    /**
     * 退出队伍
     * @param teamQuitRequest
     * @param loginUser
     * @return
     */
    boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser);
}
