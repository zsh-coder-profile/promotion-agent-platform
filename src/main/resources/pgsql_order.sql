-- ============================================================================
-- 哈根达斯 (Häagen-Dazs) 订单与优惠券系统 SQL 脚本
-- 数据库: PostgreSQL
-- ============================================================================

-- 为了支持重复执行，先删除已存在的表（注意外键依赖顺序）
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS user_coupons;
DROP TABLE IF EXISTS coupons;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;

-- ============================================================================
-- 1. 用户表 (users)
-- ============================================================================
CREATE TABLE users (
                       id SERIAL PRIMARY KEY,
                       username VARCHAR(50) NOT NULL,
                       phone VARCHAR(20) NOT NULL UNIQUE,
                       member_level VARCHAR(20) DEFAULT '普通会员',
                       points INT DEFAULT 0,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE users IS '哈根达斯用户信息表';
COMMENT ON COLUMN users.id IS '用户唯一主键ID';
COMMENT ON COLUMN users.username IS '用户昵称或姓名';
COMMENT ON COLUMN users.phone IS '绑定的手机号码';
COMMENT ON COLUMN users.member_level IS '会员等级（如：普通会员, 银卡, 金卡, 铂金）';
COMMENT ON COLUMN users.points IS '会员积分（可用于兑换冰淇淋球或周边）';
COMMENT ON COLUMN users.created_at IS '账号注册时间';

INSERT INTO users (username, phone, member_level, points) VALUES
                                                              ('冰淇淋狂热者', '13800138001', '金卡', 1250),
                                                              ('夏日小甜甜', '13800138002', '银卡', 300),
                                                              ('抹茶控_李', '13800138003', '普通会员', 50),
                                                              ('巧克力大叔', '13800138004', '铂金', 5600),
                                                              ('草莓味的风', '13800138005', '普通会员', 10),
                                                              ('香草天空', '13800138006', '银卡', 450),
                                                              ('夏威夷果仁', '13800138007', '金卡', 2100),
                                                              ('甜品胃', '13800138008', '普通会员', 0),
                                                              ('周末吃冰', '13800138009', '银卡', 880),
                                                              ('哈根达斯铁粉', '13800138010', '铂金', 8900),
                                                              ('深夜甜品', '13800138011', '普通会员', 120),
                                                              ('咖啡星人', '13800138012', '银卡', 340),
                                                              ('朗姆酒香', '13800138013', '金卡', 1100),
                                                              ('曲奇饼干', '13800138014', '普通会员', 30),
                                                              ('芒果覆盆子', '13800138015', '银卡', 500),
                                                              ('焦糖玛奇朵', '13800138016', '普通会员', 90),
                                                              ('蓝莓之夜', '13800138017', '金卡', 1750),
                                                              ('夏日限定', '13800138018', '铂金', 4300),
                                                              ('冬日火锅', '13800138019', '银卡', 620),
                                                              ('开心果爱好者', '13800138020', '普通会员', 15);

-- ============================================================================
-- 2. 商品表 (products) - 包含冰淇淋球、冰淇淋蛋糕、饮品等
-- ============================================================================
CREATE TABLE products (
                          id SERIAL PRIMARY KEY,
                          product_name VARCHAR(100) NOT NULL,
                          category VARCHAR(50) NOT NULL,
                          price DECIMAL(10, 2) NOT NULL,
                          description TEXT,
                          is_on_sale BOOLEAN DEFAULT TRUE,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE products IS '哈根达斯商品信息表';
COMMENT ON COLUMN products.id IS '商品主键ID';
COMMENT ON COLUMN products.product_name IS '商品名称（如：夏威夷果仁单球）';
COMMENT ON COLUMN products.category IS '商品分类（单球, 双球, 品脱, 冰淇淋蛋糕, 饮品）';
COMMENT ON COLUMN products.price IS '商品售卖单价';
COMMENT ON COLUMN products.description IS '商品口味描述及详细介绍';
COMMENT ON COLUMN products.is_on_sale IS '是否上架销售（TRUE: 在售, FALSE: 停售）';
COMMENT ON COLUMN products.created_at IS '商品录入时间';

INSERT INTO products (product_name, category, price, description) VALUES
                                                                      ('夏威夷果仁单球', '单球', 39.00, '经典夏威夷果仁口味，香浓丝滑。'),
                                                                      ('比利时巧克力单球', '单球', 39.00, '精选比利时黑巧克力，浓郁醇厚。'),
                                                                      ('草莓单球', '单球', 39.00, '真实的草莓果肉，酸甜可口。'),
                                                                      ('抹茶单球', '单球', 39.00, '严选日本高级抹茶粉，茶香四溢。'),
                                                                      ('香草单球', '单球', 39.00, '马达加斯加波旁香草，经典不朽。'),
                                                                      ('曲奇香奶单球', '单球', 39.00, '香草冰淇淋与巧克力曲奇的完美结合。'),
                                                                      ('朗姆酒葡萄干单球', '单球', 39.00, '含微量酒精，朗姆酒香气与葡萄干的碰撞。'),
                                                                      ('咖啡单球', '单球', 39.00, '深度烘焙咖啡豆提取，提神美味。'),
                                                                      ('夏威夷果仁+抹茶双球', '双球', 72.00, '招牌双拼，双倍快乐。'),
                                                                      ('草莓+巧克力双球', '双球', 72.00, '酸甜与苦甜的经典搭配。'),
                                                                      ('夏威夷果仁品脱(473ml)', '品脱', 98.00, '家庭装分享首选。'),
                                                                      ('抹茶品脱(473ml)', '品脱', 98.00, '大罐更满足。'),
                                                                      ('比利时巧克力品脱(473ml)', '品脱', 98.00, '适合巧克力重度爱好者。'),
                                                                      ('玫瑰倾城冰淇淋蛋糕(600g)', '冰淇淋蛋糕', 298.00, '浪漫玫瑰造型，内含草莓与香草冰淇淋。'),
                                                                      ('小王子星球冰淇淋蛋糕(1000g)', '冰淇淋蛋糕', 398.00, '适合儿童生日，巧克力与曲奇香奶口味。'),
                                                                      ('抹茶拿铁雪顶', '饮品', 45.00, '冰鲜牛奶与抹茶冰淇淋的融合。'),
                                                                      ('经典美式咖啡', '饮品', 28.00, '解腻绝佳搭配。'),
                                                                      ('仲夏野莓冰沙', '饮品', 42.00, '清爽解暑的鲜莓果冰沙。'),
                                                                      ('巧克力臻享奶昔', '饮品', 48.00, '浓郁巧克力冰淇淋打制。'),
                                                                      ('经典冰淇淋火锅套餐', '堂食套餐', 258.00, '冬季限定，多口味小球搭配热巧克力酱。');

-- ============================================================================
-- 3. 优惠券定义表 (coupons)
-- ============================================================================
CREATE TABLE coupons (
                         id SERIAL PRIMARY KEY,
                         coupon_name VARCHAR(100) NOT NULL,
                         coupon_type VARCHAR(20) NOT NULL,
                         min_amount DECIMAL(10, 2) DEFAULT 0.00,
                         discount_value DECIMAL(10, 2) NOT NULL,
                         valid_days INT NOT NULL,
                         is_active BOOLEAN DEFAULT TRUE,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE coupons IS '优惠券规则与模板定义表';
COMMENT ON COLUMN coupons.id IS '优惠券模板ID';
COMMENT ON COLUMN coupons.coupon_name IS '优惠券名称展示';
COMMENT ON COLUMN coupons.coupon_type IS '优惠类型：满减券(FULL_REDUCTION), 折扣券(DISCOUNT), 抵扣券(CASH)';
COMMENT ON COLUMN coupons.min_amount IS '使用门槛（订单满多少元可用，0代表无门槛）';
COMMENT ON COLUMN coupons.discount_value IS '抵扣金额或折扣率（满减填金额，折扣填比例如0.88代表88折）';
COMMENT ON COLUMN coupons.valid_days IS '领券后的有效天数';
COMMENT ON COLUMN coupons.is_active IS '该优惠券模板是否允许继续发放';

INSERT INTO coupons (coupon_name, coupon_type, min_amount, discount_value, valid_days) VALUES
                                                                                           ('新人专享5元无门槛券', 'CASH', 0.00, 5.00, 30),
                                                                                           ('新人专享10元无门槛券', 'CASH', 0.00, 10.00, 15),
                                                                                           ('满100减15代金券', 'FULL_REDUCTION', 100.00, 15.00, 30),
                                                                                           ('满200减40全场通用券', 'FULL_REDUCTION', 200.00, 40.00, 30),
                                                                                           ('冰淇淋蛋糕满300减50', 'FULL_REDUCTION', 300.00, 50.00, 60),
                                                                                           ('夏日冰饮满50减10', 'FULL_REDUCTION', 50.00, 10.00, 15),
                                                                                           ('周末狂欢88折券', 'DISCOUNT', 0.00, 0.88, 7),
                                                                                           ('金卡会员专属9折券', 'DISCOUNT', 0.00, 0.90, 365),
                                                                                           ('铂金会员专属8折券', 'DISCOUNT', 0.00, 0.80, 365),
                                                                                           ('品脱大满足满150减25', 'FULL_REDUCTION', 150.00, 25.00, 30),
                                                                                           ('七夕双人套餐特惠券', 'CASH', 150.00, 30.00, 7),
                                                                                           ('中秋礼盒满500减100', 'FULL_REDUCTION', 500.00, 100.00, 45),
                                                                                           ('双11狂欢满100减20', 'FULL_REDUCTION', 100.00, 20.00, 11),
                                                                                           ('年终回馈50元代金券', 'FULL_REDUCTION', 250.00, 50.00, 30),
                                                                                           ('无门槛3元随心减', 'CASH', 0.00, 3.00, 7),
                                                                                           ('老客回流满80减20', 'FULL_REDUCTION', 80.00, 20.00, 14),
                                                                                           ('冰淇淋火锅尝鲜立减30', 'FULL_REDUCTION', 200.00, 30.00, 60),
                                                                                           ('生日月专属赠金(抵用券)', 'CASH', 0.00, 50.00, 31),
                                                                                           ('女神节专享满150减38', 'FULL_REDUCTION', 150.00, 38.00, 10),
                                                                                           ('微信支付随机立减券', 'CASH', 30.00, 5.00, 3);

-- ============================================================================
-- 4. 用户领券记录表 (user_coupons)
-- ============================================================================
CREATE TABLE user_coupons (
                              id SERIAL PRIMARY KEY,
                              user_id INT NOT NULL REFERENCES users(id),
                              coupon_id INT NOT NULL REFERENCES coupons(id),
                              status VARCHAR(20) DEFAULT 'UNUSED',
                              acquired_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              used_at TIMESTAMP,
                              expires_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE user_coupons IS '用户持有的优惠券实例表';
COMMENT ON COLUMN user_coupons.id IS '实例ID';
COMMENT ON COLUMN user_coupons.user_id IS '所属用户ID';
COMMENT ON COLUMN user_coupons.coupon_id IS '对应的优惠券模板ID';
COMMENT ON COLUMN user_coupons.status IS '状态：UNUSED(未使用), USED(已使用), EXPIRED(已过期)';
COMMENT ON COLUMN user_coupons.acquired_at IS '领取时间';
COMMENT ON COLUMN user_coupons.used_at IS '实际核销使用的时间';
COMMENT ON COLUMN user_coupons.expires_at IS '过期截止时间（基于领取时间+模板有效天数计算）';

INSERT INTO user_coupons (user_id, coupon_id, status, acquired_at, expires_at) VALUES
                                                                                   (1, 3, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '25 days'),
                                                                                   (1, 8, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP + INTERVAL '355 days'),
                                                                                   (2, 1, 'USED', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP + INTERVAL '10 days'),
                                                                                   (3, 2, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '1 days', CURRENT_TIMESTAMP + INTERVAL '14 days'),
                                                                                   (4, 9, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP + INTERVAL '335 days'),
                                                                                   (4, 5, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '58 days'),
                                                                                   (5, 15, 'EXPIRED', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '3 days'),
                                                                                   (6, 4, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP + INTERVAL '18 days'),
                                                                                   (7, 8, 'USED', CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP + INTERVAL '305 days'),
                                                                                   (8, 1, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '28 days'),
                                                                                   (9, 7, 'EXPIRED', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '7 days'),
                                                                                   (10, 9, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '100 days', CURRENT_TIMESTAMP + INTERVAL '265 days'),
                                                                                   (10, 18, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '1 days', CURRENT_TIMESTAMP + INTERVAL '30 days'),
                                                                                   (11, 15, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '5 days'),
                                                                                   (12, 6, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '10 days'),
                                                                                   (13, 8, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '150 days', CURRENT_TIMESTAMP + INTERVAL '215 days'),
                                                                                   (14, 1, 'EXPIRED', CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '10 days'),
                                                                                   (15, 3, 'USED', CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP + INTERVAL '12 days'),
                                                                                   (16, 2, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP + INTERVAL '12 days'),
                                                                                   (17, 8, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP + INTERVAL '345 days'),
                                                                                   (18, 9, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP + INTERVAL '350 days'),
                                                                                   (19, 17, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '55 days'),
                                                                                   (20, 1, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '1 days', CURRENT_TIMESTAMP + INTERVAL '29 days');

-- ============================================================================
-- 5. 订单主表 (orders)
-- ============================================================================
CREATE TABLE orders (
                        id SERIAL PRIMARY KEY,
                        order_no VARCHAR(50) NOT NULL UNIQUE,
                        user_id INT NOT NULL REFERENCES users(id),
                        original_amount DECIMAL(10, 2) NOT NULL,
                        discount_amount DECIMAL(10, 2) DEFAULT 0.00,
                        actual_paid_amount DECIMAL(10, 2) NOT NULL,
                        user_coupon_id INT REFERENCES user_coupons(id),
                        status VARCHAR(20) DEFAULT 'PENDING',
                        payment_method VARCHAR(30),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        paid_at TIMESTAMP
);

COMMENT ON TABLE orders IS '哈根达斯用户订单主表';
COMMENT ON COLUMN orders.id IS '订单流水主键';
COMMENT ON COLUMN orders.order_no IS '业务订单号（如：HD202310240001）';
COMMENT ON COLUMN orders.user_id IS '下单用户ID';
COMMENT ON COLUMN orders.original_amount IS '订单原价总额（未打折前）';
COMMENT ON COLUMN orders.discount_amount IS '优惠抵扣总金额';
COMMENT ON COLUMN orders.actual_paid_amount IS '用户实际支付金额';
COMMENT ON COLUMN orders.user_coupon_id IS '使用的用户优惠券实例ID（如果使用了的话）';
COMMENT ON COLUMN orders.status IS '订单状态：PENDING(待支付), PAID(已支付), COMPLETED(已完成/已取餐), CANCELLED(已取消)';
COMMENT ON COLUMN orders.payment_method IS '支付方式：WECHAT, ALIPAY, CREDIT_CARD';
COMMENT ON COLUMN orders.created_at IS '订单创建/下单时间';
COMMENT ON COLUMN orders.paid_at IS '订单支付成功时间';

INSERT INTO orders (order_no, user_id, original_amount, discount_amount, actual_paid_amount, user_coupon_id, status, payment_method) VALUES
                                                                                                                                         ('HD202310010001', 1, 78.00, 0.00, 78.00, NULL, 'COMPLETED', 'WECHAT'),
                                                                                                                                         ('HD202310010002', 2, 39.00, 5.00, 34.00, 3, 'COMPLETED', 'ALIPAY'),
                                                                                                                                         ('HD202310020003', 3, 39.00, 0.00, 39.00, NULL, 'COMPLETED', 'WECHAT'),
                                                                                                                                         ('HD202310020004', 4, 398.00, 50.00, 348.00, NULL, 'COMPLETED', 'CREDIT_CARD'),
                                                                                                                                         ('HD202310030005', 5, 72.00, 0.00, 72.00, NULL, 'PAID', 'WECHAT'),
                                                                                                                                         ('HD202310030006', 6, 98.00, 0.00, 98.00, NULL, 'COMPLETED', 'ALIPAY'),
                                                                                                                                         ('HD202310040007', 7, 298.00, 29.80, 268.20, 9, 'COMPLETED', 'WECHAT'),
                                                                                                                                         ('HD202310040008', 8, 45.00, 0.00, 45.00, NULL, 'PENDING', NULL),
                                                                                                                                         ('HD202310050009', 9, 144.00, 0.00, 144.00, NULL, 'CANCELLED', NULL),
                                                                                                                                         ('HD202310050010', 10, 258.00, 51.60, 206.40, NULL, 'COMPLETED', 'WECHAT'),
                                                                                                                                         ('HD202310060011', 11, 42.00, 0.00, 42.00, NULL, 'COMPLETED', 'ALIPAY'),
                                                                                                                                         ('HD202310060012', 12, 196.00, 0.00, 196.00, NULL, 'COMPLETED', 'WECHAT'),
                                                                                                                                         ('HD202310070013', 13, 78.00, 0.00, 78.00, NULL, 'PAID', 'CREDIT_CARD'),
                                                                                                                                         ('HD202310070014', 14, 28.00, 0.00, 28.00, NULL, 'COMPLETED', 'WECHAT'),
                                                                                                                                         ('HD202310080015', 15, 117.00, 15.00, 102.00, 18, 'COMPLETED', 'ALIPAY'),
                                                                                                                                         ('HD202310080016', 16, 39.00, 0.00, 39.00, NULL, 'PAID', 'WECHAT'),
                                                                                                                                         ('HD202310090017', 17, 398.00, 39.80, 358.20, NULL, 'COMPLETED', 'ALIPAY'),
                                                                                                                                         ('HD202310090018', 18, 98.00, 19.60, 78.40, NULL, 'COMPLETED', 'WECHAT'),
                                                                                                                                         ('HD202310100019', 19, 72.00, 0.00, 72.00, NULL, 'PENDING', NULL),
                                                                                                                                         ('HD202310100020', 20, 84.00, 0.00, 84.00, NULL, 'CANCELLED', NULL);

-- ============================================================================
-- 6. 订单明细表 (order_items)
-- ============================================================================
CREATE TABLE order_items (
                             id SERIAL PRIMARY KEY,
                             order_id INT NOT NULL REFERENCES orders(id),
                             product_id INT NOT NULL REFERENCES products(id),
                             quantity INT NOT NULL CHECK (quantity > 0),
                             unit_price DECIMAL(10, 2) NOT NULL,
                             subtotal DECIMAL(10, 2) NOT NULL
);

COMMENT ON TABLE order_items IS '哈根达斯订单内商品明细表';
COMMENT ON COLUMN order_items.id IS '订单明细项ID';
COMMENT ON COLUMN order_items.order_id IS '关联的订单主表ID';
COMMENT ON COLUMN order_items.product_id IS '购买的商品ID';
COMMENT ON COLUMN order_items.quantity IS '购买数量';
COMMENT ON COLUMN order_items.unit_price IS '下单时的商品单价（快照）';
COMMENT ON COLUMN order_items.subtotal IS '该项小计金额（单价 * 数量）';

INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) VALUES
                                                                                   (1, 1, 1, 39.00, 39.00),
                                                                                   (1, 2, 1, 39.00, 39.00),
                                                                                   (2, 3, 1, 39.00, 39.00),
                                                                                   (3, 4, 1, 39.00, 39.00),
                                                                                   (4, 15, 1, 398.00, 398.00),
                                                                                   (5, 9, 1, 72.00, 72.00),
                                                                                   (6, 11, 1, 98.00, 98.00),
                                                                                   (7, 14, 1, 298.00, 298.00),
                                                                                   (8, 16, 1, 45.00, 45.00),
                                                                                   (9, 10, 2, 72.00, 144.00),
                                                                                   (10, 20, 1, 258.00, 258.00),
                                                                                   (11, 18, 1, 42.00, 42.00),
                                                                                   (12, 11, 1, 98.00, 98.00),
                                                                                   (12, 12, 1, 98.00, 98.00),
                                                                                   (13, 1, 2, 39.00, 78.00),
                                                                                   (14, 17, 1, 28.00, 28.00),
                                                                                   (15, 1, 1, 39.00, 39.00),
                                                                                   (15, 2, 1, 39.00, 39.00),
                                                                                   (15, 5, 1, 39.00, 39.00),
                                                                                   (16, 6, 1, 39.00, 39.00),
                                                                                   (17, 15, 1, 398.00, 398.00),
                                                                                   (18, 13, 1, 98.00, 98.00),
                                                                                   (19, 9, 1, 72.00, 72.00),
                                                                                   (20, 18, 2, 42.00, 84.00);