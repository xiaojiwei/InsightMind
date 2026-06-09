package com.graphinsight.indicator.auto.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MeasureDateRecodeExample {
    /**
     * @mbg.generated generated automatically, do not modify!
     */
    protected String orderByClause;

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    protected boolean distinct;

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    protected List<Criteria> oredCriteria;

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public MeasureDateRecodeExample() {
        oredCriteria = new ArrayList<>();
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public void setOrderByClause(String orderByClause) {
        this.orderByClause = orderByClause;
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public String getOrderByClause() {
        return orderByClause;
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public boolean isDistinct() {
        return distinct;
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public List<Criteria> getOredCriteria() {
        return oredCriteria;
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public void or(Criteria criteria) {
        oredCriteria.add(criteria);
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public Criteria or() {
        Criteria criteria = createCriteriaInternal();
        oredCriteria.add(criteria);
        return criteria;
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public Criteria createCriteria() {
        Criteria criteria = createCriteriaInternal();
        if (oredCriteria.size() == 0) {
            oredCriteria.add(criteria);
        }
        return criteria;
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    protected Criteria createCriteriaInternal() {
        Criteria criteria = new Criteria();
        return criteria;
    }

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    public void clear() {
        oredCriteria.clear();
        orderByClause = null;
        distinct = false;
    }

    protected abstract static class GeneratedCriteria {
        protected List<Criterion> criteria;

        protected GeneratedCriteria() {
            super();
            criteria = new ArrayList<>();
        }

        public boolean isValid() {
            return criteria.size() > 0;
        }

        public List<Criterion> getAllCriteria() {
            return criteria;
        }

        public List<Criterion> getCriteria() {
            return criteria;
        }

        protected void addCriterion(String condition) {
            if (condition == null) {
                throw new RuntimeException("Value for condition cannot be null");
            }
            criteria.add(new Criterion(condition));
        }

        protected void addCriterion(String condition, Object value, String property) {
            if (value == null) {
                throw new RuntimeException("Value for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value));
        }

        protected void addCriterion(String condition, Object value1, Object value2, String property) {
            if (value1 == null || value2 == null) {
                throw new RuntimeException("Between values for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value1, value2));
        }

        public Criteria andIdIsNull() {
            addCriterion("id is null");
            return (Criteria) this;
        }

        public Criteria andIdIsNotNull() {
            addCriterion("id is not null");
            return (Criteria) this;
        }

        public Criteria andIdEqualTo(Long value) {
            addCriterion("id =", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotEqualTo(Long value) {
            addCriterion("id <>", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThan(Long value) {
            addCriterion("id >", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThanOrEqualTo(Long value) {
            addCriterion("id >=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThan(Long value) {
            addCriterion("id <", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThanOrEqualTo(Long value) {
            addCriterion("id <=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdIn(List<Long> values) {
            addCriterion("id in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotIn(List<Long> values) {
            addCriterion("id not in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdBetween(Long value1, Long value2) {
            addCriterion("id between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotBetween(Long value1, Long value2) {
            addCriterion("id not between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andMCodeIsNull() {
            addCriterion("m_code is null");
            return (Criteria) this;
        }

        public Criteria andMCodeIsNotNull() {
            addCriterion("m_code is not null");
            return (Criteria) this;
        }

        public Criteria andMCodeEqualTo(String value) {
            addCriterion("m_code =", value, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeNotEqualTo(String value) {
            addCriterion("m_code <>", value, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeGreaterThan(String value) {
            addCriterion("m_code >", value, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeGreaterThanOrEqualTo(String value) {
            addCriterion("m_code >=", value, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeLessThan(String value) {
            addCriterion("m_code <", value, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeLessThanOrEqualTo(String value) {
            addCriterion("m_code <=", value, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeLike(String value) {
            addCriterion("m_code like", value, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeNotLike(String value) {
            addCriterion("m_code not like", value, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeIn(List<String> values) {
            addCriterion("m_code in", values, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeNotIn(List<String> values) {
            addCriterion("m_code not in", values, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeBetween(String value1, String value2) {
            addCriterion("m_code between", value1, value2, "mCode");
            return (Criteria) this;
        }

        public Criteria andMCodeNotBetween(String value1, String value2) {
            addCriterion("m_code not between", value1, value2, "mCode");
            return (Criteria) this;
        }

        public Criteria andMNameIsNull() {
            addCriterion("m_name is null");
            return (Criteria) this;
        }

        public Criteria andMNameIsNotNull() {
            addCriterion("m_name is not null");
            return (Criteria) this;
        }

        public Criteria andMNameEqualTo(String value) {
            addCriterion("m_name =", value, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameNotEqualTo(String value) {
            addCriterion("m_name <>", value, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameGreaterThan(String value) {
            addCriterion("m_name >", value, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameGreaterThanOrEqualTo(String value) {
            addCriterion("m_name >=", value, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameLessThan(String value) {
            addCriterion("m_name <", value, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameLessThanOrEqualTo(String value) {
            addCriterion("m_name <=", value, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameLike(String value) {
            addCriterion("m_name like", value, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameNotLike(String value) {
            addCriterion("m_name not like", value, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameIn(List<String> values) {
            addCriterion("m_name in", values, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameNotIn(List<String> values) {
            addCriterion("m_name not in", values, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameBetween(String value1, String value2) {
            addCriterion("m_name between", value1, value2, "mName");
            return (Criteria) this;
        }

        public Criteria andMNameNotBetween(String value1, String value2) {
            addCriterion("m_name not between", value1, value2, "mName");
            return (Criteria) this;
        }

        public Criteria andMDataIsNull() {
            addCriterion("m_data is null");
            return (Criteria) this;
        }

        public Criteria andMDataIsNotNull() {
            addCriterion("m_data is not null");
            return (Criteria) this;
        }

        public Criteria andMDataEqualTo(String value) {
            addCriterion("m_data =", value, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataNotEqualTo(String value) {
            addCriterion("m_data <>", value, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataGreaterThan(String value) {
            addCriterion("m_data >", value, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataGreaterThanOrEqualTo(String value) {
            addCriterion("m_data >=", value, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataLessThan(String value) {
            addCriterion("m_data <", value, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataLessThanOrEqualTo(String value) {
            addCriterion("m_data <=", value, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataLike(String value) {
            addCriterion("m_data like", value, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataNotLike(String value) {
            addCriterion("m_data not like", value, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataIn(List<String> values) {
            addCriterion("m_data in", values, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataNotIn(List<String> values) {
            addCriterion("m_data not in", values, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataBetween(String value1, String value2) {
            addCriterion("m_data between", value1, value2, "mData");
            return (Criteria) this;
        }

        public Criteria andMDataNotBetween(String value1, String value2) {
            addCriterion("m_data not between", value1, value2, "mData");
            return (Criteria) this;
        }

        public Criteria andDateTypeIsNull() {
            addCriterion("date_type is null");
            return (Criteria) this;
        }

        public Criteria andDateTypeIsNotNull() {
            addCriterion("date_type is not null");
            return (Criteria) this;
        }

        public Criteria andDateTypeEqualTo(String value) {
            addCriterion("date_type =", value, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeNotEqualTo(String value) {
            addCriterion("date_type <>", value, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeGreaterThan(String value) {
            addCriterion("date_type >", value, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeGreaterThanOrEqualTo(String value) {
            addCriterion("date_type >=", value, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeLessThan(String value) {
            addCriterion("date_type <", value, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeLessThanOrEqualTo(String value) {
            addCriterion("date_type <=", value, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeLike(String value) {
            addCriterion("date_type like", value, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeNotLike(String value) {
            addCriterion("date_type not like", value, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeIn(List<String> values) {
            addCriterion("date_type in", values, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeNotIn(List<String> values) {
            addCriterion("date_type not in", values, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeBetween(String value1, String value2) {
            addCriterion("date_type between", value1, value2, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateTypeNotBetween(String value1, String value2) {
            addCriterion("date_type not between", value1, value2, "dateType");
            return (Criteria) this;
        }

        public Criteria andDateDescIsNull() {
            addCriterion("date_desc is null");
            return (Criteria) this;
        }

        public Criteria andDateDescIsNotNull() {
            addCriterion("date_desc is not null");
            return (Criteria) this;
        }

        public Criteria andDateDescEqualTo(String value) {
            addCriterion("date_desc =", value, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescNotEqualTo(String value) {
            addCriterion("date_desc <>", value, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescGreaterThan(String value) {
            addCriterion("date_desc >", value, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescGreaterThanOrEqualTo(String value) {
            addCriterion("date_desc >=", value, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescLessThan(String value) {
            addCriterion("date_desc <", value, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescLessThanOrEqualTo(String value) {
            addCriterion("date_desc <=", value, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescLike(String value) {
            addCriterion("date_desc like", value, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescNotLike(String value) {
            addCriterion("date_desc not like", value, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescIn(List<String> values) {
            addCriterion("date_desc in", values, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescNotIn(List<String> values) {
            addCriterion("date_desc not in", values, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescBetween(String value1, String value2) {
            addCriterion("date_desc between", value1, value2, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andDateDescNotBetween(String value1, String value2) {
            addCriterion("date_desc not between", value1, value2, "dateDesc");
            return (Criteria) this;
        }

        public Criteria andCreateDateIsNull() {
            addCriterion("create_date is null");
            return (Criteria) this;
        }

        public Criteria andCreateDateIsNotNull() {
            addCriterion("create_date is not null");
            return (Criteria) this;
        }

        public Criteria andCreateDateEqualTo(Date value) {
            addCriterion("create_date =", value, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateNotEqualTo(Date value) {
            addCriterion("create_date <>", value, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateGreaterThan(Date value) {
            addCriterion("create_date >", value, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateGreaterThanOrEqualTo(Date value) {
            addCriterion("create_date >=", value, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateLessThan(Date value) {
            addCriterion("create_date <", value, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateLessThanOrEqualTo(Date value) {
            addCriterion("create_date <=", value, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateIn(List<Date> values) {
            addCriterion("create_date in", values, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateNotIn(List<Date> values) {
            addCriterion("create_date not in", values, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateBetween(Date value1, Date value2) {
            addCriterion("create_date between", value1, value2, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreateDateNotBetween(Date value1, Date value2) {
            addCriterion("create_date not between", value1, value2, "createDate");
            return (Criteria) this;
        }

        public Criteria andCreatorIsNull() {
            addCriterion("creator is null");
            return (Criteria) this;
        }

        public Criteria andCreatorIsNotNull() {
            addCriterion("creator is not null");
            return (Criteria) this;
        }

        public Criteria andCreatorEqualTo(String value) {
            addCriterion("creator =", value, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorNotEqualTo(String value) {
            addCriterion("creator <>", value, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorGreaterThan(String value) {
            addCriterion("creator >", value, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorGreaterThanOrEqualTo(String value) {
            addCriterion("creator >=", value, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorLessThan(String value) {
            addCriterion("creator <", value, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorLessThanOrEqualTo(String value) {
            addCriterion("creator <=", value, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorLike(String value) {
            addCriterion("creator like", value, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorNotLike(String value) {
            addCriterion("creator not like", value, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorIn(List<String> values) {
            addCriterion("creator in", values, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorNotIn(List<String> values) {
            addCriterion("creator not in", values, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorBetween(String value1, String value2) {
            addCriterion("creator between", value1, value2, "creator");
            return (Criteria) this;
        }

        public Criteria andCreatorNotBetween(String value1, String value2) {
            addCriterion("creator not between", value1, value2, "creator");
            return (Criteria) this;
        }

        public Criteria andUpdateDateIsNull() {
            addCriterion("update_date is null");
            return (Criteria) this;
        }

        public Criteria andUpdateDateIsNotNull() {
            addCriterion("update_date is not null");
            return (Criteria) this;
        }

        public Criteria andUpdateDateEqualTo(Date value) {
            addCriterion("update_date =", value, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateNotEqualTo(Date value) {
            addCriterion("update_date <>", value, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateGreaterThan(Date value) {
            addCriterion("update_date >", value, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateGreaterThanOrEqualTo(Date value) {
            addCriterion("update_date >=", value, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateLessThan(Date value) {
            addCriterion("update_date <", value, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateLessThanOrEqualTo(Date value) {
            addCriterion("update_date <=", value, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateIn(List<Date> values) {
            addCriterion("update_date in", values, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateNotIn(List<Date> values) {
            addCriterion("update_date not in", values, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateBetween(Date value1, Date value2) {
            addCriterion("update_date between", value1, value2, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdateDateNotBetween(Date value1, Date value2) {
            addCriterion("update_date not between", value1, value2, "updateDate");
            return (Criteria) this;
        }

        public Criteria andUpdaterIsNull() {
            addCriterion("updater is null");
            return (Criteria) this;
        }

        public Criteria andUpdaterIsNotNull() {
            addCriterion("updater is not null");
            return (Criteria) this;
        }

        public Criteria andUpdaterEqualTo(String value) {
            addCriterion("updater =", value, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterNotEqualTo(String value) {
            addCriterion("updater <>", value, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterGreaterThan(String value) {
            addCriterion("updater >", value, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterGreaterThanOrEqualTo(String value) {
            addCriterion("updater >=", value, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterLessThan(String value) {
            addCriterion("updater <", value, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterLessThanOrEqualTo(String value) {
            addCriterion("updater <=", value, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterLike(String value) {
            addCriterion("updater like", value, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterNotLike(String value) {
            addCriterion("updater not like", value, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterIn(List<String> values) {
            addCriterion("updater in", values, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterNotIn(List<String> values) {
            addCriterion("updater not in", values, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterBetween(String value1, String value2) {
            addCriterion("updater between", value1, value2, "updater");
            return (Criteria) this;
        }

        public Criteria andUpdaterNotBetween(String value1, String value2) {
            addCriterion("updater not between", value1, value2, "updater");
            return (Criteria) this;
        }
    }

    public static class Criteria extends GeneratedCriteria {
        protected Criteria() {
            super();
        }
    }

    public static class Criterion {
        private String condition;

        private Object value;

        private Object secondValue;

        private boolean noValue;

        private boolean singleValue;

        private boolean betweenValue;

        private boolean listValue;

        private String typeHandler;

        public String getCondition() {
            return condition;
        }

        public Object getValue() {
            return value;
        }

        public Object getSecondValue() {
            return secondValue;
        }

        public boolean isNoValue() {
            return noValue;
        }

        public boolean isSingleValue() {
            return singleValue;
        }

        public boolean isBetweenValue() {
            return betweenValue;
        }

        public boolean isListValue() {
            return listValue;
        }

        public String getTypeHandler() {
            return typeHandler;
        }

        protected Criterion(String condition) {
            super();
            this.condition = condition;
            this.typeHandler = null;
            this.noValue = true;
        }

        protected Criterion(String condition, Object value, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.typeHandler = typeHandler;
            if (value instanceof List<?>) {
                this.listValue = true;
            } else {
                this.singleValue = true;
            }
        }

        protected Criterion(String condition, Object value) {
            this(condition, value, null);
        }

        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.secondValue = secondValue;
            this.typeHandler = typeHandler;
            this.betweenValue = true;
        }

        protected Criterion(String condition, Object value, Object secondValue) {
            this(condition, value, secondValue, null);
        }
    }
}