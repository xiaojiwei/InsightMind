package com.graphinsight.indicator.validator;

import com.graphinsight.indicator.annotation.DimenisonFilterList;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.model.vo.DimensionFilterCreateVO;
import com.graphinsight.indicator.model.vo.DimensionFilterOperatorCreateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Author: lixiaolong
 * Date: 2022/4/14
 * Desc:
 */
@Slf4j
@Component
public class DimensionFilterValidator implements ConstraintValidator<DimenisonFilterList, LinkedList<DimensionFilterCreateVO>> {

    @Autowired
    MeasureManager measureManager;

    @Override
    public boolean isValid(LinkedList<DimensionFilterCreateVO> dimensionFilterList, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        if (!CollectionUtils.isEmpty(dimensionFilterList)) {
            for (DimensionFilterCreateVO f : dimensionFilterList) {
                String dimCode = f.getDimCode();
                LinkedList<DimensionFilterOperatorCreateVO> operatorList = f.getOperatorList();
                if (Objects.isNull(dimCode)) {
                    context.buildConstraintViolationWithTemplate("维度Code不能为空").addConstraintViolation();
                    return false;
                }
                if (Objects.isNull(f.getDimId())) {
                    context.buildConstraintViolationWithTemplate("维度ID不能为空").addConstraintViolation();
                    return false;
                }
                if (CollectionUtils.isEmpty(operatorList)) {
                    context.buildConstraintViolationWithTemplate("维度筛选项不能为空").addConstraintViolation();
                    return false;
                }
                for (DimensionFilterOperatorCreateVO o : operatorList) {
                    Integer sqlOprType = o.getSqlOprType();
                    if (Objects.isNull(sqlOprType)) {
                        context.buildConstraintViolationWithTemplate("条件筛选类型不能为空").addConstraintViolation();
                        return false;
                    }
                    if (o.getTimeRange() == null) {
                        // 除了timeRange类型的过滤器之外,维度值都不能为空
                        List<String> dataList = o.getDataList();
                        if (CollectionUtils.isEmpty(dataList)) {
                            context.buildConstraintViolationWithTemplate("维值列表不能为空").addConstraintViolation();
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
