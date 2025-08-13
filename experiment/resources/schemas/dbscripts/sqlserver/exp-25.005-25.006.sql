-- Issue 53561 - store table names at least as long as the maximum identifier length on supported primary databases
ALTER TABLE exp.DomainDescriptor ALTER COLUMN StorageTableName NVARCHAR(150);
