package com.graphinsight.indicator.thread;

import com.graphinsight.indicator.manager.DismantlingTreeManager;
import com.graphinsight.indicator.model.dto.DismantlingConfigTreeFloor;
import com.graphinsight.indicator.model.dto.DismantlingThreadQueryResult;
import com.graphinsight.indicator.model.vo.DismantlingTreeNode;
import com.graphinsight.indicator.model.vo.DismantlingTreeQuery;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Author: lixiaolong
 * Date: 2022/9/20
 * Desc:
 */
@Slf4j
public class DismantlingFloorQueryThread implements Callable<DismantlingThreadQueryResult> {


    private Integer index;
    private DismantlingTreeManager dismantlingTreeManager;
    private DismantlingConfigTreeFloor floor;
    private DismantlingTreeQuery query;

    public DismantlingFloorQueryThread(Integer index, DismantlingTreeManager dismantlingTreeManager, DismantlingConfigTreeFloor floor, DismantlingTreeQuery query) {
        this.index = index;
        this.dismantlingTreeManager = dismantlingTreeManager;
        this.floor = floor;
        this.query = query;
    }

    @Override
    public DismantlingThreadQueryResult call() throws Exception {
        List<List<DismantlingTreeNode>> lists = dismantlingTreeManager.parseFloor(floor, query);
        DismantlingThreadQueryResult result = new DismantlingThreadQueryResult();
        result.setIndex(index);
        result.setNodes(lists);
        return result;
    }


}
