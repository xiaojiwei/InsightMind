package com.graphinsight.indicator.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.model.IDaaSUserInfo;
import com.graphinsight.indicator.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.rmi.ServerException;
import java.util.List;

@Slf4j
@Service("userService")
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public User idaasRegist(IDaaSUserInfo userInfo) throws ServerException {
        User userCurrent = new User();
        BeanUtils.copyProperties(userInfo,userCurrent);
        userCurrent.setPassword("IDaaS");
        String nickName = userInfo.getName();
        userCurrent.setNickname(nickName);
        if(userInfo.getType() == IDaaSUserInfo.LI_LDAP || userInfo.getType() == IDaaSUserInfo.LI_PARTNER){
            userCurrent.setDepartmentId(userInfo.getDepartmentId());
        }
        User user = userInfo.getType() == IDaaSUserInfo.LI_LDAP ? getByUsername(userInfo.getEmail()):getByUsername(userInfo.getUsername());
        //新增或更新用户
        if (user == null){
            int insert = userMapper.insert(userCurrent);
            if (insert <= 0) {
                log.info("Regist fail, username:{}", JSON.toJSONString(userCurrent));
                throw new ServerException("Regist fail, unspecified error");
            }
        }else if (userInfo.getType() != IDaaSUserInfo.LI_LDAP){
            userCurrent.setId(user.getId());
            int update = userMapper.updateById(userCurrent);
//            int update =  userMapper.updateUserInfoById(userCurrent);
            if (update != 1) {
                log.info("update fail, username:{}", JSON.toJSONString(userCurrent));
                throw new ServerException("Update fail, unspecified error");
            }
        }
        return userInfo.getType() == IDaaSUserInfo.LI_LDAP ? getByUsername(userInfo.getEmail()):getByUsername(userInfo.getUsername());
    }

    /**
     * 根据用户名获取用户
     *
     * @param username
     * @return
     */
    @Override
    public User getByUsername(String username) {

        if (username == null || username.trim().equals("")){
            return null;
        }
        return userMapper.selectByUsername(username);
    }

    @Override
    public List<User> getByEmail(List<String> email) {
        log.info(email.toString());
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.in("email", email);
        return userMapper.selectList(wrapper);
    }
}
