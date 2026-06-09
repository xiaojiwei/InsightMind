package com.graphinsight.indicator.service;

import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.model.IDaaSUserInfo;

import java.rmi.ServerException;
import java.util.List;

public interface UserService{
    User idaasRegist(IDaaSUserInfo userInfo) throws ServerException;

    User getByUsername(String username);

    List<User> getByEmail(List<String> email);

}
