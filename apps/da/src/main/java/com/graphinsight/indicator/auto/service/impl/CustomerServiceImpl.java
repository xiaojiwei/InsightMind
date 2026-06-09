package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Customer;
import com.graphinsight.indicator.auto.mapper.CustomerMapper;
import com.graphinsight.indicator.auto.service.ICustomerService;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements ICustomerService {
}
