CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  activity_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  total_amount DECIMAL(10, 2) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_orders_order_no (order_no),
  UNIQUE KEY uk_orders_user_activity (user_id, activity_id),
  KEY idx_orders_user_id (user_id),
  KEY idx_orders_activity_id (activity_id),
  KEY idx_orders_status (status)
);

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10, 2) NOT NULL,
  created_at DATETIME NOT NULL,
  KEY idx_order_items_order_id (order_id),
  KEY idx_order_items_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS mq_message_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  business_key VARCHAR(128) NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  payload TEXT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_mq_message_logs_message_id (message_id),
  UNIQUE KEY uk_mq_message_logs_business_key (business_key),
  KEY idx_mq_message_logs_status (status)
);

CREATE TABLE IF NOT EXISTS seckill_reservations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) NOT NULL,
  message_id VARCHAR(64) NOT NULL,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64) NULL,
  failure_reason VARCHAR(512) NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NULL,
  released_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_seckill_reservations_request_id (request_id),
  UNIQUE KEY uk_seckill_reservations_message_id (message_id),
  UNIQUE KEY uk_seckill_reservations_user_activity (user_id, activity_id),
  KEY idx_seckill_reservations_status_retry (status, next_retry_at),
  KEY idx_seckill_reservations_activity (activity_id)
);
