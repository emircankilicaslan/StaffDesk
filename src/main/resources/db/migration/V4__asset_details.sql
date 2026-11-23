-- Demirbaş ayrıntıları: seri no, satın alma ve garanti bilgisi (isteğe bağlı alanlar).
ALTER TABLE asset ADD COLUMN serial_no VARCHAR(100);
ALTER TABLE asset ADD COLUMN purchase_date DATE;
ALTER TABLE asset ADD COLUMN purchase_price DECIMAL(12, 2);
ALTER TABLE asset ADD COLUMN warranty_end DATE;
ALTER TABLE asset ADD CONSTRAINT chk_asset_price CHECK (purchase_price IS NULL OR purchase_price >= 0);
CREATE INDEX idx_asset_warranty ON asset (warranty_end);
CREATE INDEX idx_asset_category ON asset (category);
