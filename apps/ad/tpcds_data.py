"""Generate TPC-DS sample data locally — no network needed."""
import os
import random
import mysql.connector
from datetime import date, timedelta, datetime

random.seed(42)
conn = mysql.connector.connect(
    host=os.getenv("TPCDS_DB_HOST", os.getenv("MYSQL_HOST", "127.0.0.1")),
    port=int(os.getenv("TPCDS_DB_PORT", os.getenv("MYSQL_PORT", "3306"))),
    user=os.getenv("TPCDS_DB_USER", os.getenv("MYSQL_USER", "root")),
    password=os.getenv("TPCDS_DB_PASSWORD", os.getenv("MYSQL_PASSWORD", "root")),
    database=os.getenv("TPCDS_DB_NAME", os.getenv("TPCDS_DB", "tpcds")),
)
cur = conn.cursor()

# ── Helper ──
def rand_date(start, end):
    delta = (end - start).days
    return start + timedelta(days=random.randint(0, delta))

# ── date_dim: ~2 years ──
print("Generating date_dim...")
start, end = date(2025, 1, 1), date(2026, 12, 31)
holidays = {(2025,12,25),(2026,1,1),(2026,12,25)}
data = []
for i, d in enumerate(range((end - start).days + 1)):
    dt = start + timedelta(days=d)
    sk = dt.strftime("%Y%m%d")
    dow = dt.isoweekday()
    is_weekend = 'Y' if dow >= 6 else 'N'
    is_holiday = 'Y' if (dt.year, dt.month, dt.day) in holidays else 'N'
    data.append((
        int(sk), dt.strftime("%Y-%m-%d"), dt,
        dt.year * 12 + dt.month, dt.isocalendar()[1] + dt.year * 53,
        (dt.month - 1) // 3 + 1 + dt.year * 4,
        dt.year, dow, dt.month, dt.day,
        (dt.month - 1) // 3 + 1,
        dt.year if dt.month > 6 else dt.year - 1,
        0, 0,
        ["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"][dow-1],
        f"Q{(dt.month-1)//3+1}{dt.year}",
        is_holiday, is_weekend, 'N',
        1, 28 if dt.month == 2 else 30 if dt.month in (4,6,9,11) else 31,
        0, 0, 'N', 'N', 'N', 'N', 'N'
    ))
cur.executemany("INSERT IGNORE INTO date_dim VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

print("Fetching valid date_sk values...")
cur.execute("SELECT d_date_sk FROM date_dim")
valid_date_sks = [row[0] for row in cur.fetchall()]
print(f"  → {len(valid_date_sks)} valid dates available")

# ── time_dim ──
print("Generating time_dim...")
data = []
for h in range(24):
    for m in [0, 15, 30, 45]:
        sk = h * 100 + m
        data.append((sk, f"{h:02d}:{m:02d}", h*3600+m*60, h, m, 0, "AM" if h<12 else "PM", "Day" if 9<=h<17 else "Evening", "", "Breakfast" if 6<=h<9 else "Lunch" if 11<=h<14 else "Dinner" if 17<=h<20 else ""))
cur.executemany("INSERT IGNORE INTO time_dim VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

# ── income_band ──
print("Generating income_band...")
data = [(1,0,30000),(2,30001,60000),(3,60001,100000),(4,100001,150000),(5,150001,999999)]
cur.executemany("INSERT IGNORE INTO income_band VALUES (%s,%s,%s)", data)

# ── ship_mode ──
print("Generating ship_mode...")
data = [(1,"SM001","EXPRESS","XP","FedEx","N"),(2,"SM002","STANDARD","ST","UPS","Y"),(3,"SM003","OVERNIGHT","ON","DHL","Y"),(4,"SM004","ECONOMY","EC","USPS","N"),(5,"SM005","2DAY","2D","FedEx","Y")]
cur.executemany("INSERT IGNORE INTO ship_mode VALUES (%s,%s,%s,%s,%s,%s)", data)

# ── reason ──
print("Generating reason...")
data = [(1,"R001","Damaged item"),(2,"R002","Wrong item shipped"),(3,"R003","Not as described"),(4,"R004","Changed mind"),(5,"R005","Late delivery"),(6,"R006","Defective product")]
cur.executemany("INSERT IGNORE INTO reason VALUES (%s,%s,%s)", data)

# ── customer_demographics ──
print("Generating customer_demographics...")
genders = ['M','F']
marital = ['M','S','W','D','U']
edu = ['High School','College','Graduate','Primary','Secondary']
credit = ['Good','Fair','Poor','Excellent']
data = []
for i in range(1, 101):
    data.append((i, random.choice(genders), random.choice(marital), random.choice(edu), random.randint(500,5000), random.choice(credit), random.randint(0,5), random.randint(0,3), random.randint(0,2)))
cur.executemany("INSERT IGNORE INTO customer_demographics VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── household_demographics ──
print("Generating household_demographics...")
buy_potential = ['High','Medium','Low','Unknown']
data = []
for i in range(1, 101):
    data.append((i, random.randint(1,5), random.choice(buy_potential), random.randint(0,6), random.randint(0,4)))
cur.executemany("INSERT IGNORE INTO household_demographics VALUES (%s,%s,%s,%s,%s)", data)

# ── customer_address ──
print("Generating customer_address...")
cities = [('Beijing','BJ','China'),('Shanghai','SH','China'),('Guangzhou','GD','China'),('Shenzhen','GD','China'),('Hangzhou','ZJ','China'),('Nanjing','JS','China'),('Wuhan','HB','China'),('Chengdu','SC','China')]
streets = ['Renmin Rd','Nanjing Rd','Chang\'an Ave','Huaihai Rd','Zhongshan Rd','Jiefang Rd','Yan\'an Rd','Heping St']
data = []
for i in range(1, 201):
    city, state, country = random.choice(cities)
    data.append((i, f"CA{str(i).zfill(12)}", str(random.randint(1,999)), random.choice(streets), random.choice(['St','Rd','Ave','Blvd']), f"Suite{random.randint(100,999)}" if random.random()>0.5 else "", city, "", state, str(random.randint(100000,999999)), country, round(random.uniform(-12,12),2), random.choice(['apartment','condo','house','rural'])))
cur.executemany("INSERT IGNORE INTO customer_address VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── customer ──
print("Generating customer...")
first_names = ['Wang','Li','Zhang','Liu','Chen','Yang','Huang','Zhao','Zhou','Wu','Xu','Sun','Ma','Zhu','Hu','Guo','Lin','He','Gao','Liang']
last_names = ['Ming','Wei','Fang','Jie','Lei','Hua','Jun','Qiang','Tao','Yan','Peng','Jing','Chao','Hui','Bo','Li','Xue','Na','Lei','Gang']
data = []
for i in range(1, 501):
    data.append((i, f"C{str(i).zfill(12)}", random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(20200101,20231231), random.randint(20200101,20231231), random.choice(['Mr.','Ms.','Mrs.','Dr.']), random.choice(first_names), random.choice(last_names), random.choice(['Y','N']), random.randint(1,28), random.randint(1,12), random.randint(1950,2005), 'China', f"login{i}", f"cust{i}@email.com", "2026-12-31"))
cur.executemany("INSERT IGNORE INTO customer VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── item ──
print("Generating item...")
categories = ['Electronics','Clothing','Sports','Home','Books','Food','Toys','Music','Garden','Health']
brands = ['BrandA','BrandB','BrandC','BrandD','BrandE']
colors = ['Red','Blue','Green','Black','White','Yellow','Purple']
data = []
for i in range(1, 301):
    cat = random.choice(categories)
    cls = f"{cat}-Class{random.randint(1,5)}"
    data.append((i, f"I{str(i).zfill(12)}", date(2025,1,1), date(2026,12,31), f"Product description for item {i}", round(random.uniform(5,500),2), round(random.uniform(2,200),2), random.randint(1,20), random.choice(brands), random.randint(1,10), cls, random.randint(1,5), cat, random.randint(1,10), f"Manu{random.randint(1,5)}", random.choice(['S','M','L','XL']), f"Form{random.randint(1,3)}", random.choice(colors), random.choice(['Each','Pack','Carton']), random.choice(['Box','Bag','Wrap']), random.randint(1,3), f"Product{i}"))
cur.executemany("INSERT IGNORE INTO item VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── store ──
print("Generating store...")
data = []
for i in range(1, 11):
    city, state, country = random.choice(cities)
    data.append((i, f"S{i:03d}", date(2025,1,1), date(2026,12,31), 0, f"Store{i}", random.randint(20,200), random.randint(5000,50000), "8AM-10PM", f"Mgr{i}", random.randint(1,5), "Urban", f"Market{i}", "", random.randint(1,3), f"Division{i}", random.randint(1,2), f"Company{i}", str(random.randint(1,999)), random.choice(streets), random.choice(['St','Rd','Ave']), f"Suite{random.randint(100,999)}", city, "", state, str(random.randint(100000,999999)), country, round(random.uniform(-12,12),2), round(random.uniform(5,15),2)))
cur.executemany("INSERT IGNORE INTO store VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── call_center ──
print("Generating call_center...")
data = []
for i in range(1, 6):
    city, state, country = random.choice(cities)
    data.append((i, f"CC{i:03d}", date(2025,1,1), date(2026,12,31), 0, 20200101, f"CallCenter{i}", f"Class{i}", random.randint(50,500), random.randint(5000,30000), "24x7", f"Mgr{i}", random.randint(1,5), f"Class{i}", f"Market{i}", "", random.randint(1,2), f"Div{i}", random.randint(1,2), f"Company{i}", str(random.randint(1,999)), random.choice(streets), random.choice(['St','Rd']), f"Suite{random.randint(100,999)}", city, "", state, str(random.randint(100000,999999)), country, round(random.uniform(-12,12),2), round(random.uniform(5,15),2)))
cur.executemany("INSERT IGNORE INTO call_center VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── web_site ──
print("Generating web_site...")
data = []
for i in range(1, 6):
    city, state, country = random.choice(cities)
    data.append((i, f"WS{i:03d}", date(2025,1,1), date(2026,12,31), f"WebSite{i}", 20200101, 0, f"Class{i}", f"Mgr{i}", random.randint(1,5), f"Class{i}", f"Market{i}", "", random.randint(1,2), f"Company{i}", str(random.randint(1,999)), random.choice(streets), random.choice(['St','Rd']), f"Suite{random.randint(100,999)}", city, "", state, str(random.randint(100000,999999)), country, round(random.uniform(-12,12),2), round(random.uniform(5,15),2)))
cur.executemany("INSERT IGNORE INTO web_site VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── catalog_page ──
print("Generating catalog_page...")
data = []
for i in range(1, 51):
    data.append((i, f"CP{str(i).zfill(12)}", random.randint(20200101,20231231), random.randint(20200101,20231231), f"Dept{random.randint(1,5)}", random.randint(1,10), random.randint(1,100), f"Page {i} description", "Sale"))
cur.executemany("INSERT IGNORE INTO catalog_page VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── web_page ──
print("Generating web_page...")
data = []
for i in range(1, 101):
    data.append((i, f"WP{str(i).zfill(12)}", date(2025,1,1), date(2026,12,31), random.randint(20200101,20231231), random.randint(20200101,20231231), random.choice(['Y','N']), random.randint(1,500), f"/page/{i}", random.choice(['product','category','home','about','contact']), random.randint(500,5000), random.randint(1,20), random.randint(0,10), random.randint(1,5)))
cur.executemany("INSERT IGNORE INTO web_page VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── warehouse ──
print("Generating warehouse...")
data = []
for i in range(1, 6):
    city, state, country = random.choice(cities)
    data.append((i, f"W{i:03d}", f"Warehouse{i}", random.randint(10000,100000), str(random.randint(1,999)), random.choice(streets), random.choice(['Rd','Ave']), f"Suite{random.randint(100,999)}", city, "", state, str(random.randint(100000,999999)), country, round(random.uniform(-12,12),2)))
cur.executemany("INSERT IGNORE INTO warehouse VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── promotion ──
print("Generating promotion...")
data = []
for i in range(1, 21):
    data.append((i, f"P{str(i).zfill(12)}", random.randint(20200101,20231231), random.randint(20200101,20231231), random.randint(1,300), round(random.uniform(100,5000),2), random.randint(100,10000), f"Promotion {i}", random.choice(['Y','N']), random.choice(['Y','N']), random.choice(['Y','N']), random.choice(['Y','N']), random.choice(['Y','N']), random.choice(['Y','N']), random.choice(['Y','N']), random.choice(['Y','N']), "Details here", random.choice(['Sale','Clearance','New']), random.choice(['Y','N'])))
cur.executemany("INSERT IGNORE INTO promotion VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)

# ── Fact Tables ──
def gen_sales(prefix, count, channels=('store',)):
    print(f"Generating {prefix}_sales...")
    data = []
    for i in range(1, count + 1):
        item_sk = random.randint(1, 300)
        cust_sk = random.randint(1, 500)
        date_sk = random.choice(valid_date_sks)
        time_sk = random.choice([0,15,30,45]) + random.randint(0,23)*100
        qty = random.randint(1, 10)
        list_price = round(random.uniform(5, 500), 2)
        discount = round(random.uniform(0, 0.4), 2)
        sales_price = round(list_price * (1 - discount), 2)
        wholesale = round(sales_price * 0.6, 2)
        ext_list = round(list_price * qty, 2)
        ext_sales = round(sales_price * qty, 2)
        ext_wholesale = round(wholesale * qty, 2)
        ext_discount = round((list_price - sales_price) * qty, 2)
        tax = round(ext_sales * 0.08, 2)
        coupon = round(random.uniform(0, ext_sales*0.1), 2)
        net_paid = round(ext_sales - coupon, 2)
        net_profit = round(net_paid - ext_wholesale, 2)
        promo_sk = random.randint(1, 20)
        row = [date_sk, time_sk, item_sk, cust_sk,
               random.randint(1,100), random.randint(1,100), random.randint(1,200),
               random.randint(1,10), promo_sk, i, qty, wholesale, list_price, sales_price,
               ext_discount, ext_sales, ext_wholesale, ext_list, tax, coupon, net_paid,
               round(net_paid+tax,2), net_profit]
        if 'catalog' in prefix:
            row[7] = random.randint(1,5)  # call_center
            ship_date = date_sk + random.randint(1,7)
            ship_cost = round(random.uniform(5, 50), 2)
            ship_mode = random.randint(1,5)
            warehouse = random.randint(1,5)
            page = random.randint(1,50)
            row_extra = [ship_date, cust_sk if random.random()>0.3 else random.randint(1,500),
                        random.randint(1,100), random.randint(1,100), random.randint(1,200),
                        random.randint(1,100), random.randint(1,100), random.randint(1,200),
                        page, ship_mode, warehouse, None, None, None, None, None,
                        ship_cost, None, None, None]
        elif 'web' in prefix:
            row[7] = random.randint(1,5)  # web_site
            ship_date = date_sk + random.randint(1,7)
            ship_cost = round(random.uniform(5, 50), 2)
            ship_mode = random.randint(1,5)
            warehouse = random.randint(1,5)
            page = random.randint(1,100)
            row_extra = [ship_date, None, None, None, None, page, ship_mode, warehouse, None, None, None,
                        None, None, None, None, ship_cost, None, None, None, None]
        else:
            row_extra = []
        data.append(tuple(row + row_extra))
    return data

# Simplified approach: generate data for all fact tables
print("Generating store_sales (2000 rows)...")
data = []
for i in range(1, 2001):
    item_sk = random.randint(1, 300)
    cust_sk = random.randint(1, 500)
    date_sk = random.choice(valid_date_sks)
    time_sk = random.choice([0,15,30,45]) + random.randint(0,23)*100
    qty = random.randint(1, 10)
    list_price = round(random.uniform(5, 500), 2)
    disc = round(random.uniform(0, 0.4), 2)
    sales_price = round(list_price * (1 - disc), 2)
    wholesale = round(sales_price * 0.6, 2)
    ext_sales = round(sales_price * qty, 2)
    ext_wholesale = round(wholesale * qty, 2)
    ext_list = round(list_price * qty, 2)
    ext_discount = round((list_price - sales_price) * qty, 2)
    ext_tax = round(ext_sales * 0.08, 2)
    coupon = round(random.uniform(0, ext_sales*0.1), 2)
    net_paid = round(ext_sales - coupon, 2)
    net_profit = round(net_paid - ext_wholesale, 2)
    data.append((date_sk, time_sk, item_sk, cust_sk, random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(1,10), random.randint(1,20), i, qty, wholesale, list_price, sales_price, ext_discount, ext_sales, ext_wholesale, ext_list, ext_tax, coupon, net_paid, round(net_paid+ext_tax,2), net_profit))
cur.executemany("INSERT IGNORE INTO store_sales VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

print("Generating store_returns (200 rows)...")
data = []
for i in range(1, 201):
    item_sk = random.randint(1, 300)
    cust_sk = random.randint(1, 500)
    date_sk = random.choice(valid_date_sks)
    time_sk = random.choice([0,15,30,45]) + random.randint(0,23)*100
    qty = random.randint(1, 3)
    amt = round(random.uniform(10, 300), 2)
    tax = round(amt * 0.08, 2)
    fee = round(random.uniform(5, 20), 2)
    ship = round(random.uniform(5, 30), 2)
    refund = round(amt + tax - fee, 2)
    net_loss = round(amt * 0.4 + fee + ship, 2)
    data.append((date_sk, time_sk, item_sk, cust_sk, random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(1,10), random.randint(1,6), i, qty, amt, tax, round(amt+tax,2), fee, ship, refund, round(amt*0.2,2), round(amt*0.3,2), net_loss))
cur.executemany("INSERT IGNORE INTO store_returns VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

print("Generating catalog_sales (800 rows)...")
data = []
for i in range(1, 801):
    item_sk = random.randint(1, 300)
    cust_sk = random.randint(1, 500)
    ship_cust = cust_sk if random.random()>0.2 else random.randint(1,500)
    date_sk = random.choice(valid_date_sks)
    ship_date = date_sk + random.randint(1,7)
    time_sk = random.choice([0,15,30,45]) + random.randint(0,23)*100
    qty = random.randint(1, 10)
    list_price = round(random.uniform(5, 500), 2)
    disc = round(random.uniform(0, 0.4), 2)
    sales_price = round(list_price * (1 - disc), 2)
    wholesale = round(sales_price * 0.6, 2)
    ext_sales = round(sales_price * qty, 2)
    ext_wholesale = round(wholesale * qty, 2)
    ext_discount = round((list_price - sales_price) * qty, 2)
    ext_tax = round(ext_sales * 0.08, 2)
    coupon = round(random.uniform(0, ext_sales*0.1), 2)
    ship_cost = round(random.uniform(5, 50), 2)
    net_paid = round(ext_sales - coupon, 2)
    net_profit = round(net_paid - ext_wholesale - ship_cost, 2)
    data.append((date_sk, time_sk, ship_date, cust_sk, ship_cust, item_sk, random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(1,5), random.randint(1,50), random.randint(1,5), random.randint(1,5), random.randint(1,20), i, qty, wholesale, list_price, sales_price, ext_discount, ext_sales, ext_wholesale, round(list_price*qty,2), ext_tax, coupon, ship_cost, net_paid, round(net_paid+ext_tax,2), round(net_paid+ship_cost,2), round(net_paid+ext_tax+ship_cost,2), net_profit))
cur.executemany("INSERT IGNORE INTO catalog_sales VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

print("Generating catalog_returns (80 rows)...")
data = []
for i in range(1, 81):
    item_sk = random.randint(1, 300)
    cust_sk = random.randint(1, 500)
    date_sk = random.choice(valid_date_sks)
    time_sk = random.choice([0,15,30,45]) + random.randint(0,23)*100
    qty = random.randint(1, 3)
    amt = round(random.uniform(10, 300), 2)
    tax = round(amt * 0.08, 2)
    fee = round(random.uniform(5, 20), 2)
    ship = round(random.uniform(5, 30), 2)
    net_loss = round(amt * 0.4 + fee + ship, 2)
    data.append((date_sk, time_sk, item_sk, cust_sk, random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(1,500), random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(1,5), random.randint(1,50), random.randint(1,5), random.randint(1,5), random.randint(1,6), i, qty, amt, tax, round(amt+tax,2), fee, ship, round(amt-tax-fee,2), round(amt*0.2,2), round(amt*0.3,2), net_loss))
cur.executemany("INSERT IGNORE INTO catalog_returns VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

print("Generating web_sales (800 rows)...")
data = []
for i in range(1, 801):
    item_sk = random.randint(1, 300)
    cust_sk = random.randint(1, 500)
    ship_cust = cust_sk if random.random()>0.2 else random.randint(1,500)
    date_sk = random.choice(valid_date_sks)
    ship_date = date_sk + random.randint(1,7)
    time_sk = random.choice([0,15,30,45]) + random.randint(0,23)*100
    qty = random.randint(1, 10)
    list_price = round(random.uniform(5, 500), 2)
    disc = round(random.uniform(0, 0.4), 2)
    sales_price = round(list_price * (1 - disc), 2)
    wholesale = round(sales_price * 0.6, 2)
    ext_sales = round(sales_price * qty, 2)
    ext_wholesale = round(wholesale * qty, 2)
    ext_discount = round((list_price - sales_price) * qty, 2)
    ext_tax = round(ext_sales * 0.08, 2)
    coupon = round(random.uniform(0, ext_sales*0.1), 2)
    ship_cost = round(random.uniform(5, 50), 2)
    net_paid = round(ext_sales - coupon, 2)
    net_profit = round(net_paid - ext_wholesale - ship_cost, 2)
    data.append((date_sk, time_sk, ship_date, item_sk, cust_sk, ship_cust, random.randint(1,100), random.randint(1,5), random.randint(1,5), random.randint(1,5), random.randint(1,20), i, qty, wholesale, list_price, sales_price, ext_discount, ext_sales, ext_wholesale, round(list_price*qty,2), ext_tax, coupon, ship_cost, net_paid, round(net_paid+ext_tax,2), round(net_paid+ship_cost,2), round(net_paid+ext_tax+ship_cost,2), net_profit))
cur.executemany("INSERT IGNORE INTO web_sales VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

print("Generating web_returns (80 rows)...")
data = []
for i in range(1, 81):
    item_sk = random.randint(1, 300)
    cust_sk = random.randint(1, 500)
    date_sk = random.choice(valid_date_sks)
    time_sk = random.choice([0,15,30,45]) + random.randint(0,23)*100
    qty = random.randint(1, 3)
    amt = round(random.uniform(10, 300), 2)
    tax = round(amt * 0.08, 2)
    fee = round(random.uniform(5, 20), 2)
    ship = round(random.uniform(5, 30), 2)
    net_loss = round(amt * 0.4 + fee + ship, 2)
    data.append((date_sk, time_sk, item_sk, cust_sk, random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(1,500), random.randint(1,100), random.randint(1,100), random.randint(1,200), random.randint(1,100), random.randint(1,6), i, qty, amt, tax, round(amt+tax,2), fee, ship, round(amt-tax-fee,2), round(amt*0.2,2), round(amt*0.3,2), net_loss))
cur.executemany("INSERT IGNORE INTO web_returns VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

print("Generating inventory (500 rows)...")
data = []
for i in range(1, 501):
    data.append((random.choice(valid_date_sks), random.randint(1, 300), random.randint(1, 5), random.randint(0, 10000)))
cur.executemany("INSERT IGNORE INTO inventory VALUES (%s,%s,%s,%s)", data)
conn.commit()
print(f"  → {len(data)} rows")

conn.close()
print("\nDone! TPC-DS sample data generated.")
