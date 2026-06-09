package com.graphinsight.indicator.service;

public interface IDaaSValidateService {

    String validate(String accessToken, String scope) throws Exception;
}
