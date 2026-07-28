package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.enums.ContributionCalculationType;
import org.springframework.stereotype.Component;

/**
 * Date: 2022/6/14
 * Desc:
 */
@Component
public class ContributionStrategyHolder {

    public static ContributionStrategy getStrategy(ContributionCalculationType contributionCalculationType){

        switch (contributionCalculationType){
            case ADDITION:
                return new AdditionStrategy();
            case SUBTRACTION:
                return new SubtractionStrategy();
            case MULTIPLICATION:
                return new MultiplicationStrategy();
            case DIVISION:
                return new DivisionStrategy();
            case TWO_FACTOR:
                return new TwoFactorStrategy();
            default:
                return new DefaultStrategy();
        }
    }
}
