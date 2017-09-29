ALTER TABLE exp.PropertyDescriptor ADD COLUMN mvIndicatorStorageColumnName VARCHAR(120);

SELECT core.executeJavaUpgradeCode('saveMvIndicatorStorageNames');
