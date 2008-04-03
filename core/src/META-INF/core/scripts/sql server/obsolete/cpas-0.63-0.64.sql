-- Rename an object, failing silently
CREATE PROCEDURE RenameObject(@oldName AS VARCHAR(200), @newName AS VARCHAR(200)) AS
    IF OBJECT_ID(@oldName) IS NOT NULL
        EXEC sp_rename @oldName, @newName
GO

-- Rename PKs & UQ
EXEC RenameObject 'PK_PropertySet', 'PK_PropertySets'
EXEC RenameObject 'UQ_PropertySet', 'UQ_PropertySets'


DROP PROCEDURE RenameObject
GO