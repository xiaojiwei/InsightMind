package com.graphinsight.indicator.dismantling;

import com.graphinsight.indicator.util.UuidUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Class DismantlingTreeTest
 * Description: DismantlingTreeTest
 *
 * @Author: tongxuejie <tongxuejie@graphinsight.com>
 * Date 2024/2/23 09:45
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@ActiveProfiles("dev")
public class DismantlingTreeTest {
    @Test
    public void taskIdGenerateTest() {
        String taskId = UuidUtil.getUUID32();
        assert taskId.length() == 32;
    }
}
