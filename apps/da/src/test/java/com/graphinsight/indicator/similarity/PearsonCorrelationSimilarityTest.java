package com.graphinsight.indicator.similarity;

import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.junit.Test;

import java.io.File;

/**
 * Author: lixiaolong
 * Date: 2022/12/15
 * Desc:
 */
public class PearsonCorrelationSimilarityTest {

    @Test
    public void demo() throws Exception{

        DataModel model = new FileDataModel(new File("/Users/lixiaolong5/Desktop/user.csv"));

        ItemSimilarity similarity = new PearsonCorrelationSimilarity(model);
        double v = similarity.itemSimilarity(101, 101);
        System.out.println(v);
        v = similarity.itemSimilarity(101, 102);
        System.out.println(v);
        v = similarity.itemSimilarity(101, 103);
        System.out.println(v);
        v = similarity.itemSimilarity(101, 104);
        System.out.println(v);
        v = similarity.itemSimilarity(101, 105);
        System.out.println(v);

        // //明确与给定用户最相似的一组用户
        // UserNeighborhood neighborhood = new NearestNUserNeighborhood(1, similarity, model);
        // //合并上述所有组件为用户推荐物品
        // Recommender recommender = new GenericUserBasedRecommender(
        //         model, neighborhood, similarity);
        // List<RecommendedItem> recommendations =
        //         recommender.recommend(1, 1);
        // for (RecommendedItem recommendation : recommendations) {
        //     System.out.println(recommendation);
        // }

    }
}
