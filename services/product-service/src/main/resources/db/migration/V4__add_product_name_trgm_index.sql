CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_product_name_trgm ON product USING GIN (name gin_trgm_ops);
-- CREATE INDEX idx_products_search_trgm ON product USING gin ((name || ' ' || coalesce(brand, '')) gin_trgm_ops);