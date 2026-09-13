CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS ops;
CREATE SCHEMA IF NOT EXISTS analytics;

COMMENT ON SCHEMA app IS 'Authoritative Hyped application data';
COMMENT ON SCHEMA ops IS 'Durable jobs, outbox, and operational state';
COMMENT ON SCHEMA analytics IS 'Privacy-safe product analytics';

