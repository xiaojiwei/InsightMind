package com.graphinsight.indicator.util;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.ColumnViewType;
import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.dto.DimensionConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2022/12/14
 * Desc:
 */
public class DateDimensionCreateUtil {

    public static final String YEAR_PARTTERN = "'%Y'";
    public static final String MONTH_PARTTERN = "'%Y-%m'";
    public static final String DAY_PARTTERN = "'%Y-%m-%d'";


    public static List<Dimension> listDimensions(ColumnViewType viewType, DimensionConfig dimensionConfig) {
        List<Dimension> result = new ArrayList<>();
        Dimension masterDimension = null;
        String masterDiemnsionParttern = "";
        switch (viewType){
            case DAY:
                masterDimension = getDimension(ViewType.DAY, dimensionConfig);
                result.add(masterDimension);
                result.add(getDimension(ViewType.WEEK,dimensionConfig));
                result.add(getDimension(ViewType.MONTH,dimensionConfig));
                result.add(getDimension(ViewType.SEASON,dimensionConfig));
                result.add(getDimension(ViewType.YEAR,dimensionConfig));
                masterDiemnsionParttern = DAY_PARTTERN;
                break;

            case MONTH:
                masterDimension = getDimension(ViewType.MONTH, dimensionConfig);
                result.add(masterDimension);
                result.add(getDimension(ViewType.SEASON,dimensionConfig));
                result.add(getDimension(ViewType.YEAR,dimensionConfig));
                masterDiemnsionParttern = MONTH_PARTTERN;
                break;

            case YEAR:
                masterDimension = getDimension(ViewType.YEAR, dimensionConfig);
                result.add(masterDimension);
                masterDiemnsionParttern = YEAR_PARTTERN;
                break;
            default:
                throw IndicatorParamNotValidException.error("viewType不合法");
        }
        masterDimension.setEnName(dimensionConfig.getDimension().getEnName());
        masterDimension.setDescription(dimensionConfig.getDimension().getDescription());
        dimensionConfig.setMasterDimension(masterDimension);
        dimensionConfig.setMasterDiemnsionParttern(masterDiemnsionParttern);

        return result;
    }

    public static DimensionDimtableConnect getDimensionDimtableConnect(ViewType viewType, Integer dimId) {
        DimensionDimtableConnect connect = new DimensionDimtableConnect();
        String primaryKey = "";
        String valueColumn = "";
        switch (viewType){
            case DAY:
                primaryKey = "date_key";
                valueColumn = "date_key";
                break;
            case WEEK:
                primaryKey = "week_key";
                valueColumn = "week_key";
                break;
            case MONTH:
                primaryKey = "month_key";
                valueColumn = "month_key";
                break;
            case SEASON:
                primaryKey = "quarter_key";
                valueColumn = "quarter_key";
                break;
            case YEAR:
                primaryKey = "year_key";
                valueColumn = "year_key";
                break;
            default:
                throw IndicatorParamNotValidException.error("viewType不合法");
        }
        connect.initCreate();
        connect.setDimTableName("dim_base_date");
        connect.setSchemaName("eps_dim");
        connect.setDimPrimaryKey(primaryKey);
        connect.setDimValueColumn(valueColumn);
        connect.setCreateTime(LocalDateTime.now());
        connect.setUpdateTime(LocalDateTime.now());
        connect.setDimId(dimId);
        return connect;
    }

    public static Dimension getDimension(ViewType viewType, DimensionConfig dimensionConfig) {
        Dimension dimension = new Dimension();
        String enNameSuffix = "";
        String cnNameSuffix = "";
        switch (viewType){
            case DAY:
                enNameSuffix = "_day_system";
                cnNameSuffix = "_D";
                break;
            case WEEK:
                enNameSuffix = "_week_system";
                cnNameSuffix = "_W";
                break;
            case MONTH:
                enNameSuffix = "_month_system";
                cnNameSuffix = "_M";
                break;
            case SEASON:
                enNameSuffix = "_season_system";
                cnNameSuffix = "_Q";
                break;
            case YEAR:
                enNameSuffix = "_year_system";
                cnNameSuffix = "_Y";
                break;
        }
        String monthCode = dimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
        dimension.setCode(monthCode);
        dimension.setLeafCategoryId(dimensionConfig.getDimension().getLeafCategoryId());
        dimension.setEnName(dimensionConfig.getDimension().getEnName() + enNameSuffix);
        dimension.setCnName(dimensionConfig.getDimension().getCnName() + cnNameSuffix);
        dimension.setViewType(viewType.getValue());
        dimension.setDescription(dimensionConfig.getDimension().getDescription() + cnNameSuffix);
        dimension.setDimType(DimType.STD_WITH_TABLE.getValue());
        return dimension;
    }
}
