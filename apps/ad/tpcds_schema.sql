-- TPC-DS Schema for MySQL (24 tables)

USE tpcds;

-- ===================== Dimension Tables (17) =====================

CREATE TABLE date_dim (
    d_date_sk           INT PRIMARY KEY COMMENT 'Date surrogate key',
    d_date_id           CHAR(16) NOT NULL COMMENT 'Date ID (YYYY-MM-DD)',
    d_date              DATE COMMENT 'Calendar date',
    d_month_seq         INT COMMENT 'Month sequence',
    d_week_seq          INT COMMENT 'Week sequence',
    d_quarter_seq       INT COMMENT 'Quarter sequence',
    d_year              INT COMMENT 'Year',
    d_dow               INT COMMENT 'Day of week',
    d_moy               INT COMMENT 'Month of year',
    d_dom               INT COMMENT 'Day of month',
    d_qoy               INT COMMENT 'Quarter of year',
    d_fy_year           INT COMMENT 'Fiscal year',
    d_fy_quarter_seq    INT COMMENT 'Fiscal quarter sequence',
    d_fy_week_seq       INT COMMENT 'Fiscal week sequence',
    d_day_name          CHAR(9) COMMENT 'Day name',
    d_quarter_name      CHAR(6) COMMENT 'Quarter name',
    d_holiday           CHAR(1) COMMENT 'Holiday flag',
    d_weekend           CHAR(1) COMMENT 'Weekend flag',
    d_following_holiday CHAR(1) COMMENT 'Following holiday flag',
    d_first_dom         INT COMMENT 'First day of month',
    d_last_dom          INT COMMENT 'Last day of month',
    d_same_day_ly       INT COMMENT 'Same day last year SK',
    d_same_day_lq       INT COMMENT 'Same day last quarter SK',
    d_current_day       CHAR(1) COMMENT 'Current day flag',
    d_current_week      CHAR(1) COMMENT 'Current week flag',
    d_current_month     CHAR(1) COMMENT 'Current month flag',
    d_current_quarter   CHAR(1) COMMENT 'Current quarter flag',
    d_current_year      CHAR(1) COMMENT 'Current year flag'
) COMMENT='Date dimension table';

CREATE TABLE time_dim (
    t_time_sk       INT PRIMARY KEY COMMENT 'Time surrogate key',
    t_time_id       CHAR(16) NOT NULL COMMENT 'Time ID',
    t_time          INT COMMENT 'Time in seconds',
    t_hour          INT COMMENT 'Hour',
    t_minute        INT COMMENT 'Minute',
    t_second        INT COMMENT 'Second',
    t_am_pm         CHAR(2) COMMENT 'AM/PM',
    t_shift         CHAR(20) COMMENT 'Shift',
    t_sub_shift     CHAR(20) COMMENT 'Sub shift',
    t_meal_time     CHAR(20) COMMENT 'Meal time'
) COMMENT='Time dimension table';

CREATE TABLE customer (
    c_customer_sk           INT PRIMARY KEY COMMENT 'Customer surrogate key',
    c_customer_id           CHAR(16) NOT NULL COMMENT 'Customer ID',
    c_current_cdemo_sk      INT COMMENT 'Current demographic SK',
    c_current_hdemo_sk      INT COMMENT 'Current household demographic SK',
    c_current_addr_sk       INT COMMENT 'Current address SK',
    c_first_shipto_date_sk  INT COMMENT 'First ship-to date SK',
    c_first_sales_date_sk   INT COMMENT 'First sales date SK',
    c_salutation            CHAR(10) COMMENT 'Salutation',
    c_first_name            CHAR(20) COMMENT 'First name',
    c_last_name             CHAR(30) COMMENT 'Last name',
    c_preferred_cust_flag   CHAR(1) COMMENT 'Preferred customer flag',
    c_birth_day             INT COMMENT 'Birth day',
    c_birth_month           INT COMMENT 'Birth month',
    c_birth_year            INT COMMENT 'Birth year',
    c_birth_country         VARCHAR(20) COMMENT 'Birth country',
    c_login                 CHAR(13) COMMENT 'Login',
    c_email_address         CHAR(50) COMMENT 'Email address',
    c_last_review_date      CHAR(10) COMMENT 'Last review date'
) COMMENT='Customer dimension table';

CREATE TABLE customer_address (
    ca_address_sk       INT PRIMARY KEY COMMENT 'Address surrogate key',
    ca_address_id       CHAR(16) NOT NULL COMMENT 'Address ID',
    ca_street_number    CHAR(10) COMMENT 'Street number',
    ca_street_name      VARCHAR(60) COMMENT 'Street name',
    ca_street_type      CHAR(15) COMMENT 'Street type',
    ca_suite_number     CHAR(10) COMMENT 'Suite number',
    ca_city             VARCHAR(60) COMMENT 'City',
    ca_county           VARCHAR(30) COMMENT 'County',
    ca_state            CHAR(2) COMMENT 'State',
    ca_zip              CHAR(10) COMMENT 'Zip code',
    ca_country          VARCHAR(20) COMMENT 'Country',
    ca_gmt_offset       DECIMAL(5,2) COMMENT 'GMT offset',
    ca_location_type    CHAR(20) COMMENT 'Location type'
) COMMENT='Customer address dimension table';

CREATE TABLE customer_demographics (
    cd_demo_sk              INT PRIMARY KEY COMMENT 'Demographic surrogate key',
    cd_gender               CHAR(1) COMMENT 'Gender',
    cd_marital_status       CHAR(1) COMMENT 'Marital status',
    cd_education_status     CHAR(20) COMMENT 'Education status',
    cd_purchase_estimate    INT COMMENT 'Purchase estimate',
    cd_credit_rating        CHAR(10) COMMENT 'Credit rating',
    cd_dep_count            INT COMMENT 'Dependent count',
    cd_dep_employed_count   INT COMMENT 'Employed dependent count',
    cd_dep_college_count    INT COMMENT 'College dependent count'
) COMMENT='Customer demographics dimension table';

CREATE TABLE household_demographics (
    hd_demo_sk          INT PRIMARY KEY COMMENT 'Household demographic SK',
    hd_income_band_sk   INT COMMENT 'Income band SK',
    hd_buy_potential    CHAR(15) COMMENT 'Buy potential',
    hd_dep_count        INT COMMENT 'Dependent count',
    hd_vehicle_count    INT COMMENT 'Vehicle count'
) COMMENT='Household demographics dimension table';

CREATE TABLE income_band (
    ib_income_band_sk   INT PRIMARY KEY COMMENT 'Income band SK',
    ib_lower_bound      INT COMMENT 'Lower bound',
    ib_upper_bound      INT COMMENT 'Upper bound'
) COMMENT='Income band dimension table';

CREATE TABLE item (
    i_item_sk           INT PRIMARY KEY COMMENT 'Item surrogate key',
    i_item_id           CHAR(16) NOT NULL COMMENT 'Item ID',
    i_rec_start_date    DATE COMMENT 'Record start date',
    i_rec_end_date      DATE COMMENT 'Record end date',
    i_item_desc         VARCHAR(200) COMMENT 'Item description',
    i_current_price     DECIMAL(7,2) COMMENT 'Current price',
    i_wholesale_cost    DECIMAL(7,2) COMMENT 'Wholesale cost',
    i_brand_id          INT COMMENT 'Brand ID',
    i_brand             CHAR(50) COMMENT 'Brand name',
    i_class_id          INT COMMENT 'Class ID',
    i_class             CHAR(50) COMMENT 'Class name',
    i_category_id       INT COMMENT 'Category ID',
    i_category          CHAR(50) COMMENT 'Category name',
    i_manufact_id       INT COMMENT 'Manufacturer ID',
    i_manufact          CHAR(50) COMMENT 'Manufacturer name',
    i_size              CHAR(20) COMMENT 'Size',
    i_formulation       CHAR(20) COMMENT 'Formulation',
    i_color             CHAR(20) COMMENT 'Color',
    i_units             CHAR(10) COMMENT 'Units',
    i_container         CHAR(10) COMMENT 'Container',
    i_manager_id        INT COMMENT 'Manager ID',
    i_product_name      CHAR(50) COMMENT 'Product name'
) COMMENT='Item dimension table';

CREATE TABLE store (
    s_store_sk              INT PRIMARY KEY COMMENT 'Store surrogate key',
    s_store_id              CHAR(16) NOT NULL COMMENT 'Store ID',
    s_rec_start_date        DATE COMMENT 'Record start date',
    s_rec_end_date          DATE COMMENT 'Record end date',
    s_closed_date_sk        INT COMMENT 'Closed date SK',
    s_store_name            VARCHAR(50) COMMENT 'Store name',
    s_number_employees      INT COMMENT 'Number of employees',
    s_floor_space           INT COMMENT 'Floor space',
    s_hours                 CHAR(20) COMMENT 'Hours',
    s_manager               VARCHAR(40) COMMENT 'Manager',
    s_market_id             INT COMMENT 'Market ID',
    s_geography_class       VARCHAR(100) COMMENT 'Geography class',
    s_market_desc           VARCHAR(100) COMMENT 'Market description',
    s_market_manager        VARCHAR(40) COMMENT 'Market manager',
    s_division_id           INT COMMENT 'Division ID',
    s_division_name         VARCHAR(50) COMMENT 'Division name',
    s_company_id            INT COMMENT 'Company ID',
    s_company_name          VARCHAR(50) COMMENT 'Company name',
    s_street_number         VARCHAR(10) COMMENT 'Street number',
    s_street_name           VARCHAR(60) COMMENT 'Street name',
    s_street_type           CHAR(15) COMMENT 'Street type',
    s_suite_number          CHAR(10) COMMENT 'Suite number',
    s_city                  VARCHAR(60) COMMENT 'City',
    s_county                VARCHAR(30) COMMENT 'County',
    s_state                 CHAR(2) COMMENT 'State',
    s_zip                   CHAR(10) COMMENT 'Zip code',
    s_country               VARCHAR(20) COMMENT 'Country',
    s_gmt_offset            DECIMAL(5,2) COMMENT 'GMT offset',
    s_tax_precentage        DECIMAL(5,2) COMMENT 'Tax percentage'
) COMMENT='Store dimension table';

CREATE TABLE call_center (
    cc_call_center_sk       INT PRIMARY KEY COMMENT 'Call center surrogate key',
    cc_call_center_id       CHAR(16) NOT NULL COMMENT 'Call center ID',
    cc_rec_start_date       DATE COMMENT 'Record start date',
    cc_rec_end_date         DATE COMMENT 'Record end date',
    cc_closed_date_sk       INT COMMENT 'Closed date SK',
    cc_open_date_sk         INT COMMENT 'Open date SK',
    cc_name                 VARCHAR(50) COMMENT 'Call center name',
    cc_class                VARCHAR(50) COMMENT 'Class',
    cc_employees            INT COMMENT 'Employees',
    cc_sq_ft                INT COMMENT 'Square feet',
    cc_hours                CHAR(20) COMMENT 'Hours',
    cc_manager              VARCHAR(40) COMMENT 'Manager',
    cc_mkt_id               INT COMMENT 'Market ID',
    cc_mkt_class            CHAR(50) COMMENT 'Market class',
    cc_mkt_desc             VARCHAR(100) COMMENT 'Market description',
    cc_market_manager       VARCHAR(40) COMMENT 'Market manager',
    cc_division             INT COMMENT 'Division',
    cc_division_name        VARCHAR(50) COMMENT 'Division name',
    cc_company              INT COMMENT 'Company',
    cc_company_name         CHAR(50) COMMENT 'Company name',
    cc_street_number        CHAR(10) COMMENT 'Street number',
    cc_street_name          VARCHAR(60) COMMENT 'Street name',
    cc_street_type          CHAR(15) COMMENT 'Street type',
    cc_suite_number         CHAR(10) COMMENT 'Suite number',
    cc_city                 VARCHAR(60) COMMENT 'City',
    cc_county               VARCHAR(30) COMMENT 'County',
    cc_state                CHAR(2) COMMENT 'State',
    cc_zip                  CHAR(10) COMMENT 'Zip code',
    cc_country              VARCHAR(20) COMMENT 'Country',
    cc_gmt_offset           DECIMAL(5,2) COMMENT 'GMT offset',
    cc_tax_percentage       DECIMAL(5,2) COMMENT 'Tax percentage'
) COMMENT='Call center dimension table';

CREATE TABLE catalog_page (
    cp_catalog_page_sk      INT PRIMARY KEY COMMENT 'Catalog page SK',
    cp_catalog_page_id      CHAR(16) NOT NULL COMMENT 'Catalog page ID',
    cp_start_date_sk        INT COMMENT 'Start date SK',
    cp_end_date_sk          INT COMMENT 'End date SK',
    cp_department           VARCHAR(50) COMMENT 'Department',
    cp_catalog_number       INT COMMENT 'Catalog number',
    cp_catalog_page_number  INT COMMENT 'Catalog page number',
    cp_description          VARCHAR(100) COMMENT 'Description',
    cp_type                 VARCHAR(100) COMMENT 'Type'
) COMMENT='Catalog page dimension table';

CREATE TABLE web_site (
    web_site_sk             INT PRIMARY KEY COMMENT 'Web site SK',
    web_site_id             CHAR(16) NOT NULL COMMENT 'Web site ID',
    web_rec_start_date      DATE COMMENT 'Record start date',
    web_rec_end_date        DATE COMMENT 'Record end date',
    web_name                VARCHAR(50) COMMENT 'Web site name',
    web_open_date_sk        INT COMMENT 'Open date SK',
    web_close_date_sk       INT COMMENT 'Close date SK',
    web_class               VARCHAR(50) COMMENT 'Class',
    web_manager             VARCHAR(40) COMMENT 'Manager',
    web_mkt_id              INT COMMENT 'Market ID',
    web_mkt_class           VARCHAR(50) COMMENT 'Market class',
    web_mkt_desc            VARCHAR(100) COMMENT 'Market description',
    web_market_manager      VARCHAR(40) COMMENT 'Market manager',
    web_company_id          INT COMMENT 'Company ID',
    web_company_name        CHAR(50) COMMENT 'Company name',
    web_street_number       CHAR(10) COMMENT 'Street number',
    web_street_name         VARCHAR(60) COMMENT 'Street name',
    web_street_type         CHAR(15) COMMENT 'Street type',
    web_suite_number        CHAR(10) COMMENT 'Suite number',
    web_city                VARCHAR(60) COMMENT 'City',
    web_county              VARCHAR(30) COMMENT 'County',
    web_state               CHAR(2) COMMENT 'State',
    web_zip                 CHAR(10) COMMENT 'Zip code',
    web_country             VARCHAR(20) COMMENT 'Country',
    web_gmt_offset          DECIMAL(5,2) COMMENT 'GMT offset',
    web_tax_percentage      DECIMAL(5,2) COMMENT 'Tax percentage'
) COMMENT='Web site dimension table';

CREATE TABLE web_page (
    wp_web_page_sk          INT PRIMARY KEY COMMENT 'Web page SK',
    wp_web_page_id          CHAR(16) NOT NULL COMMENT 'Web page ID',
    wp_rec_start_date       DATE COMMENT 'Record start date',
    wp_rec_end_date         DATE COMMENT 'Record end date',
    wp_creation_date_sk     INT COMMENT 'Creation date SK',
    wp_access_date_sk       INT COMMENT 'Access date SK',
    wp_autogen_flag         CHAR(1) COMMENT 'Auto-generated flag',
    wp_customer_sk          INT COMMENT 'Customer SK',
    wp_url                  VARCHAR(100) COMMENT 'URL',
    wp_type                 CHAR(50) COMMENT 'Type',
    wp_char_count           INT COMMENT 'Character count',
    wp_link_count           INT COMMENT 'Link count',
    wp_image_count          INT COMMENT 'Image count',
    wp_max_ad_count         INT COMMENT 'Max ad count'
) COMMENT='Web page dimension table';

CREATE TABLE warehouse (
    w_warehouse_sk          INT PRIMARY KEY COMMENT 'Warehouse SK',
    w_warehouse_id          CHAR(16) NOT NULL COMMENT 'Warehouse ID',
    w_warehouse_name        VARCHAR(20) COMMENT 'Warehouse name',
    w_warehouse_sq_ft       INT COMMENT 'Square feet',
    w_street_number         CHAR(10) COMMENT 'Street number',
    w_street_name           VARCHAR(60) COMMENT 'Street name',
    w_street_type           CHAR(15) COMMENT 'Street type',
    w_suite_number          CHAR(10) COMMENT 'Suite number',
    w_city                  VARCHAR(60) COMMENT 'City',
    w_county                VARCHAR(30) COMMENT 'County',
    w_state                 CHAR(2) COMMENT 'State',
    w_zip                   CHAR(10) COMMENT 'Zip code',
    w_country               VARCHAR(20) COMMENT 'Country',
    w_gmt_offset            DECIMAL(5,2) COMMENT 'GMT offset'
) COMMENT='Warehouse dimension table';

CREATE TABLE promotion (
    p_promo_sk              INT PRIMARY KEY COMMENT 'Promotion surrogate key',
    p_promo_id              CHAR(16) NOT NULL COMMENT 'Promotion ID',
    p_start_date_sk         INT COMMENT 'Start date SK',
    p_end_date_sk           INT COMMENT 'End date SK',
    p_item_sk               INT COMMENT 'Item SK',
    p_cost                  DECIMAL(15,2) COMMENT 'Cost',
    p_response_target       INT COMMENT 'Response target',
    p_promo_name            CHAR(50) COMMENT 'Promotion name',
    p_channel_dmail         CHAR(1) COMMENT 'Direct mail channel',
    p_channel_email         CHAR(1) COMMENT 'Email channel',
    p_channel_catalog       CHAR(1) COMMENT 'Catalog channel',
    p_channel_tv            CHAR(1) COMMENT 'TV channel',
    p_channel_radio         CHAR(1) COMMENT 'Radio channel',
    p_channel_press         CHAR(1) COMMENT 'Press channel',
    p_channel_event         CHAR(1) COMMENT 'Event channel',
    p_channel_demo          CHAR(1) COMMENT 'Demo channel',
    p_channel_details       VARCHAR(100) COMMENT 'Channel details',
    p_purpose               CHAR(15) COMMENT 'Purpose',
    p_discount_active       CHAR(1) COMMENT 'Discount active flag'
) COMMENT='Promotion dimension table';

CREATE TABLE reason (
    r_reason_sk             INT PRIMARY KEY COMMENT 'Reason surrogate key',
    r_reason_id             CHAR(16) NOT NULL COMMENT 'Reason ID',
    r_reason_desc           CHAR(100) COMMENT 'Reason description'
) COMMENT='Reason dimension table';

CREATE TABLE ship_mode (
    sm_ship_mode_sk         INT PRIMARY KEY COMMENT 'Ship mode surrogate key',
    sm_ship_mode_id         CHAR(16) NOT NULL COMMENT 'Ship mode ID',
    sm_type                 CHAR(30) COMMENT 'Type',
    sm_code                 CHAR(10) COMMENT 'Code',
    sm_carrier              CHAR(20) COMMENT 'Carrier',
    sm_contract             CHAR(20) COMMENT 'Contract'
) COMMENT='Ship mode dimension table';

-- ===================== Fact Tables (7) =====================

CREATE TABLE store_sales (
    ss_sold_date_sk         INT COMMENT 'Sold date SK',
    ss_sold_time_sk         INT COMMENT 'Sold time SK',
    ss_item_sk              INT COMMENT 'Item SK',
    ss_customer_sk          INT COMMENT 'Customer SK',
    ss_cdemo_sk             INT COMMENT 'Customer demographic SK',
    ss_hdemo_sk             INT COMMENT 'Household demographic SK',
    ss_addr_sk              INT COMMENT 'Address SK',
    ss_store_sk             INT COMMENT 'Store SK',
    ss_promo_sk             INT COMMENT 'Promotion SK',
    ss_ticket_number        BIGINT COMMENT 'Ticket number',
    ss_quantity             INT COMMENT 'Quantity',
    ss_wholesale_cost       DECIMAL(7,2) COMMENT 'Wholesale cost',
    ss_list_price           DECIMAL(7,2) COMMENT 'List price',
    ss_sales_price          DECIMAL(7,2) COMMENT 'Sales price',
    ss_ext_discount_amt     DECIMAL(7,2) COMMENT 'Extended discount amount',
    ss_ext_sales_price      DECIMAL(7,2) COMMENT 'Extended sales price',
    ss_ext_wholesale_cost   DECIMAL(7,2) COMMENT 'Extended wholesale cost',
    ss_ext_list_price       DECIMAL(7,2) COMMENT 'Extended list price',
    ss_ext_tax              DECIMAL(7,2) COMMENT 'Extended tax',
    ss_coupon_amt           DECIMAL(7,2) COMMENT 'Coupon amount',
    ss_net_paid             DECIMAL(7,2) COMMENT 'Net paid',
    ss_net_paid_inc_tax     DECIMAL(7,2) COMMENT 'Net paid including tax',
    ss_net_profit           DECIMAL(7,2) COMMENT 'Net profit',
    PRIMARY KEY (ss_item_sk, ss_ticket_number)
) COMMENT='Store sales fact table';

CREATE TABLE store_returns (
    sr_returned_date_sk     INT COMMENT 'Returned date SK',
    sr_return_time_sk       INT COMMENT 'Return time SK',
    sr_item_sk              INT COMMENT 'Item SK',
    sr_customer_sk          INT COMMENT 'Customer SK',
    sr_cdemo_sk             INT COMMENT 'Customer demographic SK',
    sr_hdemo_sk             INT COMMENT 'Household demographic SK',
    sr_addr_sk              INT COMMENT 'Address SK',
    sr_store_sk             INT COMMENT 'Store SK',
    sr_reason_sk            INT COMMENT 'Reason SK',
    sr_ticket_number        BIGINT COMMENT 'Ticket number',
    sr_return_quantity      INT COMMENT 'Return quantity',
    sr_return_amt           DECIMAL(7,2) COMMENT 'Return amount',
    sr_return_tax           DECIMAL(7,2) COMMENT 'Return tax',
    sr_return_amt_inc_tax   DECIMAL(7,2) COMMENT 'Return amount including tax',
    sr_fee                  DECIMAL(7,2) COMMENT 'Fee',
    sr_return_ship_cost     DECIMAL(7,2) COMMENT 'Return ship cost',
    sr_refunded_cash        DECIMAL(7,2) COMMENT 'Refunded cash',
    sr_reversed_charge      DECIMAL(7,2) COMMENT 'Reversed charge',
    sr_store_credit         DECIMAL(7,2) COMMENT 'Store credit',
    sr_net_loss             DECIMAL(7,2) COMMENT 'Net loss',
    PRIMARY KEY (sr_item_sk, sr_ticket_number)
) COMMENT='Store returns fact table';

CREATE TABLE catalog_sales (
    cs_sold_date_sk         INT COMMENT 'Sold date SK',
    cs_sold_time_sk         INT COMMENT 'Sold time SK',
    cs_ship_date_sk         INT COMMENT 'Ship date SK',
    cs_bill_customer_sk     INT COMMENT 'Bill customer SK',
    cs_ship_customer_sk     INT COMMENT 'Ship customer SK',
    cs_item_sk              INT COMMENT 'Item SK',
    cs_bill_cdemo_sk        INT COMMENT 'Bill customer demographic SK',
    cs_bill_hdemo_sk        INT COMMENT 'Bill household demographic SK',
    cs_bill_addr_sk         INT COMMENT 'Bill address SK',
    cs_ship_cdemo_sk        INT COMMENT 'Ship customer demographic SK',
    cs_ship_hdemo_sk        INT COMMENT 'Ship household demographic SK',
    cs_ship_addr_sk         INT COMMENT 'Ship address SK',
    cs_call_center_sk       INT COMMENT 'Call center SK',
    cs_catalog_page_sk      INT COMMENT 'Catalog page SK',
    cs_ship_mode_sk         INT COMMENT 'Ship mode SK',
    cs_warehouse_sk         INT COMMENT 'Warehouse SK',
    cs_promo_sk             INT COMMENT 'Promotion SK',
    cs_order_number         BIGINT COMMENT 'Order number',
    cs_quantity             INT COMMENT 'Quantity',
    cs_wholesale_cost       DECIMAL(7,2) COMMENT 'Wholesale cost',
    cs_list_price           DECIMAL(7,2) COMMENT 'List price',
    cs_sales_price          DECIMAL(7,2) COMMENT 'Sales price',
    cs_ext_discount_amt     DECIMAL(7,2) COMMENT 'Extended discount amount',
    cs_ext_sales_price      DECIMAL(7,2) COMMENT 'Extended sales price',
    cs_ext_wholesale_cost   DECIMAL(7,2) COMMENT 'Extended wholesale cost',
    cs_ext_list_price       DECIMAL(7,2) COMMENT 'Extended list price',
    cs_ext_tax              DECIMAL(7,2) COMMENT 'Extended tax',
    cs_coupon_amt           DECIMAL(7,2) COMMENT 'Coupon amount',
    cs_ext_ship_cost        DECIMAL(7,2) COMMENT 'Extended ship cost',
    cs_net_paid             DECIMAL(7,2) COMMENT 'Net paid',
    cs_net_paid_inc_tax     DECIMAL(7,2) COMMENT 'Net paid including tax',
    cs_net_paid_inc_ship    DECIMAL(7,2) COMMENT 'Net paid including ship',
    cs_net_paid_inc_ship_tax DECIMAL(7,2) COMMENT 'Net paid including ship and tax',
    cs_net_profit           DECIMAL(7,2) COMMENT 'Net profit',
    PRIMARY KEY (cs_item_sk, cs_order_number)
) COMMENT='Catalog sales fact table';

CREATE TABLE catalog_returns (
    cr_returned_date_sk     INT COMMENT 'Returned date SK',
    cr_returned_time_sk     INT COMMENT 'Returned time SK',
    cr_item_sk              INT COMMENT 'Item SK',
    cr_refunded_customer_sk INT COMMENT 'Refunded customer SK',
    cr_refunded_cdemo_sk    INT COMMENT 'Refunded customer demographic SK',
    cr_refunded_hdemo_sk    INT COMMENT 'Refunded household demographic SK',
    cr_refunded_addr_sk     INT COMMENT 'Refunded address SK',
    cr_returning_customer_sk INT COMMENT 'Returning customer SK',
    cr_returning_cdemo_sk   INT COMMENT 'Returning customer demographic SK',
    cr_returning_hdemo_sk   INT COMMENT 'Returning household demographic SK',
    cr_returning_addr_sk    INT COMMENT 'Returning address SK',
    cr_call_center_sk       INT COMMENT 'Call center SK',
    cr_catalog_page_sk      INT COMMENT 'Catalog page SK',
    cr_ship_mode_sk         INT COMMENT 'Ship mode SK',
    cr_warehouse_sk         INT COMMENT 'Warehouse SK',
    cr_reason_sk            INT COMMENT 'Reason SK',
    cr_order_number         BIGINT COMMENT 'Order number',
    cr_return_quantity      INT COMMENT 'Return quantity',
    cr_return_amount        DECIMAL(7,2) COMMENT 'Return amount',
    cr_return_tax           DECIMAL(7,2) COMMENT 'Return tax',
    cr_return_amt_inc_tax   DECIMAL(7,2) COMMENT 'Return amount including tax',
    cr_fee                  DECIMAL(7,2) COMMENT 'Fee',
    cr_return_ship_cost     DECIMAL(7,2) COMMENT 'Return ship cost',
    cr_refunded_cash        DECIMAL(7,2) COMMENT 'Refunded cash',
    cr_reversed_charge      DECIMAL(7,2) COMMENT 'Reversed charge',
    cr_store_credit         DECIMAL(7,2) COMMENT 'Store credit',
    cr_net_loss             DECIMAL(7,2) COMMENT 'Net loss',
    PRIMARY KEY (cr_item_sk, cr_order_number)
) COMMENT='Catalog returns fact table';

CREATE TABLE web_sales (
    ws_sold_date_sk         INT COMMENT 'Sold date SK',
    ws_sold_time_sk         INT COMMENT 'Sold time SK',
    ws_ship_date_sk         INT COMMENT 'Ship date SK',
    ws_item_sk              INT COMMENT 'Item SK',
    ws_bill_customer_sk     INT COMMENT 'Bill customer SK',
    ws_ship_customer_sk     INT COMMENT 'Ship customer SK',
    ws_web_page_sk          INT COMMENT 'Web page SK',
    ws_web_site_sk          INT COMMENT 'Web site SK',
    ws_ship_mode_sk         INT COMMENT 'Ship mode SK',
    ws_warehouse_sk         INT COMMENT 'Warehouse SK',
    ws_promo_sk             INT COMMENT 'Promotion SK',
    ws_order_number         BIGINT COMMENT 'Order number',
    ws_quantity             INT COMMENT 'Quantity',
    ws_wholesale_cost       DECIMAL(7,2) COMMENT 'Wholesale cost',
    ws_list_price           DECIMAL(7,2) COMMENT 'List price',
    ws_sales_price          DECIMAL(7,2) COMMENT 'Sales price',
    ws_ext_discount_amt     DECIMAL(7,2) COMMENT 'Extended discount amount',
    ws_ext_sales_price      DECIMAL(7,2) COMMENT 'Extended sales price',
    ws_ext_wholesale_cost   DECIMAL(7,2) COMMENT 'Extended wholesale cost',
    ws_ext_list_price       DECIMAL(7,2) COMMENT 'Extended list price',
    ws_ext_tax              DECIMAL(7,2) COMMENT 'Extended tax',
    ws_coupon_amt           DECIMAL(7,2) COMMENT 'Coupon amount',
    ws_ext_ship_cost        DECIMAL(7,2) COMMENT 'Extended ship cost',
    ws_net_paid             DECIMAL(7,2) COMMENT 'Net paid',
    ws_net_paid_inc_tax     DECIMAL(7,2) COMMENT 'Net paid including tax',
    ws_net_paid_inc_ship    DECIMAL(7,2) COMMENT 'Net paid including ship',
    ws_net_paid_inc_ship_tax DECIMAL(7,2) COMMENT 'Net paid including ship and tax',
    ws_net_profit           DECIMAL(7,2) COMMENT 'Net profit',
    PRIMARY KEY (ws_item_sk, ws_order_number)
) COMMENT='Web sales fact table';

CREATE TABLE web_returns (
    wr_returned_date_sk     INT COMMENT 'Returned date SK',
    wr_returned_time_sk     INT COMMENT 'Returned time SK',
    wr_item_sk              INT COMMENT 'Item SK',
    wr_refunded_customer_sk INT COMMENT 'Refunded customer SK',
    wr_refunded_cdemo_sk    INT COMMENT 'Refunded customer demographic SK',
    wr_refunded_hdemo_sk    INT COMMENT 'Refunded household demographic SK',
    wr_refunded_addr_sk     INT COMMENT 'Refunded address SK',
    wr_returning_customer_sk INT COMMENT 'Returning customer SK',
    wr_returning_cdemo_sk   INT COMMENT 'Returning customer demographic SK',
    wr_returning_hdemo_sk   INT COMMENT 'Returning household demographic SK',
    wr_returning_addr_sk    INT COMMENT 'Returning address SK',
    wr_web_page_sk          INT COMMENT 'Web page SK',
    wr_reason_sk            INT COMMENT 'Reason SK',
    wr_order_number         BIGINT COMMENT 'Order number',
    wr_return_quantity      INT COMMENT 'Return quantity',
    wr_return_amt           DECIMAL(7,2) COMMENT 'Return amount',
    wr_return_tax           DECIMAL(7,2) COMMENT 'Return tax',
    wr_return_amt_inc_tax   DECIMAL(7,2) COMMENT 'Return amount including tax',
    wr_fee                  DECIMAL(7,2) COMMENT 'Fee',
    wr_return_ship_cost     DECIMAL(7,2) COMMENT 'Return ship cost',
    wr_refunded_cash        DECIMAL(7,2) COMMENT 'Refunded cash',
    wr_reversed_charge      DECIMAL(7,2) COMMENT 'Reversed charge',
    wr_account_credit       DECIMAL(7,2) COMMENT 'Account credit',
    wr_net_loss             DECIMAL(7,2) COMMENT 'Net loss',
    PRIMARY KEY (wr_item_sk, wr_order_number)
) COMMENT='Web returns fact table';

CREATE TABLE inventory (
    inv_date_sk             INT COMMENT 'Date SK',
    inv_item_sk             INT COMMENT 'Item SK',
    inv_warehouse_sk        INT COMMENT 'Warehouse SK',
    inv_quantity_on_hand    INT COMMENT 'Quantity on hand',
    PRIMARY KEY (inv_date_sk, inv_item_sk, inv_warehouse_sk)
) COMMENT='Inventory fact table';
