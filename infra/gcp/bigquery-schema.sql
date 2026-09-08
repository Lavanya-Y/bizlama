CREATE TABLE IF NOT EXISTS `PROJECT_ID_PLACEHOLDER.bizlama_analytics.operational_events` (
  event_id STRING NOT NULL,
  kitchen_id STRING,
  event_type STRING NOT NULL,
  aggregate_type STRING,
  aggregate_id STRING,
  payload_json JSON,
  occurred_at TIMESTAMP NOT NULL,
  ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
)
PARTITION BY DATE(occurred_at)
CLUSTER BY kitchen_id, event_type;


CREATE TABLE IF NOT EXISTS `PROJECT_ID_PLACEHOLDER.bizlama_analytics.order_facts` (
  order_id STRING NOT NULL,
  kitchen_id STRING NOT NULL,
  location_id STRING,
  status STRING NOT NULL,
  channel STRING,
  gross_total NUMERIC,
  created_at TIMESTAMP NOT NULL,
  item_count INT64,
  updated_at TIMESTAMP
)
PARTITION BY DATE(created_at)
CLUSTER BY kitchen_id, status, channel;


CREATE TABLE IF NOT EXISTS `PROJECT_ID_PLACEHOLDER.bizlama_analytics.inventory_daily_snapshots` (
  snapshot_date DATE NOT NULL,
  kitchen_id STRING NOT NULL,
  location_id STRING,
  ingredient_id STRING NOT NULL,
  quantity NUMERIC NOT NULL,
  base_unit STRING NOT NULL,
  expiring_soon_quantity NUMERIC,
  inventory_value NUMERIC
)
PARTITION BY snapshot_date
CLUSTER BY kitchen_id, ingredient_id;


CREATE TABLE IF NOT EXISTS `PROJECT_ID_PLACEHOLDER.bizlama_analytics.stock_movement_facts` (
  movement_id STRING NOT NULL,
  kitchen_id STRING NOT NULL,
  ingredient_id STRING NOT NULL,
  movement_type STRING NOT NULL,
  quantity_change NUMERIC NOT NULL,
  unit STRING NOT NULL,
  reference_type STRING,
  reference_id STRING,
  occurred_at TIMESTAMP NOT NULL
)
PARTITION BY DATE(occurred_at)
CLUSTER BY kitchen_id, ingredient_id, movement_type;


CREATE TABLE IF NOT EXISTS `PROJECT_ID_PLACEHOLDER.bizlama_analytics.feedback_insights` (
  feedback_id STRING NOT NULL,
  kitchen_id STRING NOT NULL,
  dish_id STRING,
  recipe_version_id STRING,
  rating INT64,
  sentiment_score FLOAT64,
  themes ARRAY<STRING>,
  source STRING,
  occurred_at TIMESTAMP NOT NULL
)
PARTITION BY DATE(occurred_at)
CLUSTER BY kitchen_id, dish_id;


CREATE TABLE IF NOT EXISTS `PROJECT_ID_PLACEHOLDER.bizlama_analytics.ai_action_audit` (
  action_id STRING NOT NULL,
  kitchen_id STRING NOT NULL,
  action_type STRING NOT NULL,
  confidence FLOAT64 NOT NULL,
  decision STRING NOT NULL,
  normalized_item_id STRING,
  model_name STRING,
  occurred_at TIMESTAMP NOT NULL
)
PARTITION BY DATE(occurred_at)
CLUSTER BY kitchen_id, action_type, decision;


CREATE TABLE IF NOT EXISTS `PROJECT_ID_PLACEHOLDER.bizlama_analytics.raw_foodkeeper_guidance` (
  source_item_id STRING,
  category STRING,
  item_name STRING NOT NULL,
  storage_method STRING,
  min_days INT64,
  max_days INT64,
  source_url STRING,
  source_updated_at TIMESTAMP,
  loaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
)
CLUSTER BY category, item_name;