-- Rename IX index name, failing silently 
CREATE PROCEDURE RenameIndex(@owner AS VARCHAR(20), @tableName AS VARCHAR(100), @oldName AS VARCHAR(100), @newName AS VARCHAR(100)) AS
	IF (SELECT COUNT(*) FROM sysindexes WHERE NAME = @oldName) > 0
		BEGIN
			DECLARE @fullOldName VARCHAR(200)
			SET @fullOldName = @owner + '.' + @tableName + '.' + @oldName
			EXEC sp_rename @fullOldName, @newName
		END
GO


EXEC RenameIndex 'dbo', 'Issues', 'Issues_AssignedTo', 'IX_Issues_AssignedTo'
EXEC RenameIndex 'dbo', 'Issues', 'Issues_Status', 'IX_Issues_Status'
GO

DROP PROCEDURE RenameIndex
GO