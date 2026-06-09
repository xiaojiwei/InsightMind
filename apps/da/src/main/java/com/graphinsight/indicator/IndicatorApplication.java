package com.graphinsight.indicator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan(value = {"com.graphinsight.indicator.auto.mapper","com.graphinsight.indicator.doris.mapper"})
public class IndicatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(IndicatorApplication.class, args);
	}

}
