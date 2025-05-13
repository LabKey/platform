-- We don't expect storage column names this large, but this allows us to remove some truncation code
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN StorageColumnName NVARCHAR(255);
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN mvIndicatorStorageColumnName NVARCHAR(275);
