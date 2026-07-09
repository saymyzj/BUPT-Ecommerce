CREATE TABLE IF NOT EXISTS products (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  description TEXT NULL,
  price DECIMAL(10, 2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS product_stocks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  total_stock INT NOT NULL,
  available_stock INT NOT NULL,
  reserved_stock INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_product_stocks_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS seckill_activities (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  activity_name VARCHAR(128) NOT NULL,
  seckill_price DECIMAL(10, 2) NOT NULL,
  seckill_stock INT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  KEY idx_seckill_activities_product_id (product_id)
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

CREATE TABLE IF NOT EXISTS seckill_publish_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id VARCHAR(64) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NULL,
  last_error VARCHAR(512) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_seckill_publish_events_message_id (message_id),
  KEY idx_seckill_publish_events_status_retry (status, next_retry_at)
);

CREATE TABLE IF NOT EXISTS scheduled_task_locks (
  task_name VARCHAR(128) PRIMARY KEY,
  locked_by VARCHAR(64) NOT NULL,
  locked_until DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS seckill_waitlist_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_seckill_waitlist_user_activity (user_id, activity_id),
  KEY idx_seckill_waitlist_activity_status_id (activity_id, status, id)
);
