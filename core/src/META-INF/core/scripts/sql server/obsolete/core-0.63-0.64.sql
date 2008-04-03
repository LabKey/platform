-- Rename an object, failing silently
CREATE PROCEDURE RenameObject(@oldName AS VARCHAR(200), @newName AS VARCHAR(200)) AS
    IF OBJECT_ID(@oldName) IS NOT NULL
        EXEC sp_rename @oldName, @newName
GO

-- Fix one PK name
EXEC RenameObject 'PK_Users', 'PK_UsersData'
GO

DROP PROCEDURE RenameObject
GO