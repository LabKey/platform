ALTER TABLE exp.PropertyDescriptor ADD mvIndicatorStorageColumnName NVARCHAR(120);
GO

EXEC core.executeJavaUpgradeCode 'saveMvIndicatorStorageNames';
