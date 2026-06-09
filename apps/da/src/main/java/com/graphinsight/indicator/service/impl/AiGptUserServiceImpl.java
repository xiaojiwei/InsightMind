package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.bean.BeanUtil;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.vo.AiGptLimitVo;
import com.graphinsight.indicator.service.AiGptUserService;
import com.graphinsight.indicator.model.vo.AiGptUserPageParam;
import com.graphinsight.indicator.model.vo.AiGptUserVO;
import com.graphinsight.indicator.entity.AiGptUser;
import com.graphinsight.indicator.auto.mapper.AiGptUserMapper;
import com.graphinsight.indicator.service.AuthService;
import com.graphinsight.indicator.service.SpaceEmployeeService;
import com.graphinsight.indicator.service.SpaceService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author houfenglei
 */
@Slf4j
@Service
public class AiGptUserServiceImpl extends ServiceImpl<AiGptUserMapper, AiGptUser> implements AiGptUserService {

    @Override
    public IPage<AiGptUser> page(AiGptUserPageParam pageParam) {
        LambdaQueryWrapper<AiGptUser> queryWrapper = Wrappers.lambdaQuery(AiGptUser.class);
        return super.page(new Page<>(pageParam.getPageNo(), pageParam.getPageSize()), queryWrapper);
    }

    @Override
    public void update(AiGptUserVO VO) {
        AiGptUser toUpdate = BeanUtil.copyProperties(VO, AiGptUser.class);
        super.updateById(toUpdate);
    }

    @Override
    public void save(AiGptUserVO VO) {
        AiGptUser toSave = BeanUtil.copyProperties(VO, AiGptUser.class);
        super.save(toSave);
    }

    @Override
    public void delete(Long id) {
        super.removeById(id);
    }


    @Value("#{'${ai.default.meas:MEAS_d032c7f897934775843396cba45cd254,MEAS_6c10208c2e4c458f8255d39a3f4310eb}'.split(',')}")
    private Set<String> defaultMeas;

    @Autowired
    private UserManager userManager;
    @Autowired
    ITSpaceService itSpaceService;
    @Autowired
    private AuthService authService;

    @Override
    public AiGptLimitVo limitUser() {
        AiGptLimitVo limitVo = new AiGptLimitVo();
        List<AiGptUser> listUser = getBaseMapper().selectList(Wrappers.<AiGptUser>lambdaQuery().eq(AiGptUser::getUserName, UserThreadLocalUtil.getUserName()));
        if (!listUser.isEmpty()) {
            limitVo.setRange(true);
        }
        Set<String> authMeasCodes = new HashSet<>();
        UserContext userContext = userManager.getUserContext(itSpaceService.getAiSpaceById().getId(), UserThreadLocalUtil.getUserName());
        log.info("userContext is :{}", userContext);
        if (userContext == null || userContext.getAuthMeasures().isEmpty()) {
            // 设置上默认权限
            authMeasCodes = defaultMeas;
            // 异步执行
            Set<String> finalAuthMeasCodes = authMeasCodes;
            Long spaceId = itSpaceService.getAiSpaceById().getId();
            String userCode = UserThreadLocalUtil.getUserName();
            String userNickname = UserThreadLocalUtil.get().getNickname();
            Long userId = Long.valueOf(UserThreadLocalUtil.get().getId());
            CompletableFuture.runAsync(() -> {
                authService.authPeopleApply(finalAuthMeasCodes, spaceId, userCode, userNickname, userId);
            }).handle((r, e) -> {
                if (e != null) {
                    log.error("异步执行异常信息: {}", e.getMessage(), e);
                }
                return null;
            });
        } else {
            for (com.graphinsight.indicator.auto.entity.Measure measure : userContext.getAuthMeasures()) {
                if (null != measure && null != measure.getCode()) {
                    authMeasCodes.add(measure.getCode());
                }
            }
        }
        if (authMeasCodes.equals(defaultMeas)) {
            limitVo.setIsDefault(true);
        }

        return limitVo;
    }
}