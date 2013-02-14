CREATE SCHEMA dataintegration
GO

CREATE PROCEDURE dataintegration.addDataIntegrationColumns @schemaName NVARCHAR(100), @tableName NVARCHAR(100)
AS
DECLARE @sql NVARCHAR(1000)
SELECT @sql = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD  ' +
     '_txRowVersion ROWVERSION, ' +
     '_txLastUpdated DATETIME, ' +
     '_txTransactionId INT, ' +
     '_txNote NVARCHAR(1000)';
EXEC sp_executesql @sql;
SELECT @sql = 'ALTER TABLE [' + @schemaName + '].[' + @tableName + '] ADD CONSTRAINT [_DF_' + @tableName + '_updated] ' +
    'DEFAULT getutcdate() FOR [_txLastUpdated]';
EXEC sp_executesql @sql;

GO