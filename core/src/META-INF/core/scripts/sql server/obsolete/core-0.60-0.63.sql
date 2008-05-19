/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Rename the randomly assigned PK names to simply 'PK_<tablename>'
CREATE PROCEDURE ChangeBogusIndexName(@owner AS VARCHAR(20), @type AS VARCHAR(10), @tableName AS VARCHAR(100)) AS
	DECLARE @bogusName varchar(200)
	SET @bogusName = (SELECT name FROM sysobjects WHERE (name LIKE @type + '\_\_' + @tableName + '\_\_%' ESCAPE '\'))

    IF @bogusName IS NOT NULL
        BEGIN
    	    DECLARE @newName VARCHAR(200)
    	    SET @bogusName = @owner + '.' + @bogusName
	        SET @newName = @type + '_' + @tableName
    	    EXEC sp_rename @bogusName, @newName
	    END
GO

EXEC ChangeBogusIndexName 'dbo', 'PK', 'Logins'
GO

DROP PROCEDURE ChangeBogusIndexName
GO

-- Rename an object, failing silently
CREATE PROCEDURE RenameObject(@oldName AS VARCHAR(200), @newName AS VARCHAR(200)) AS
    IF OBJECT_ID(@oldName) IS NOT NULL
        EXEC sp_rename @oldName, @newName
GO

-- Rename PKs & FKs & UQs
EXEC RenameObject 'Users_PK', 'PK_Users'
EXEC RenameObject 'Documents_PK', 'PK_Documents'
EXEC RenameObject 'SqlScripts_PK', 'PK_SqlScripts'
EXEC RenameObject 'Modules_PK', 'PK_Modules'
EXEC RenameObject 'Principals_PK', 'PK_Principals'
EXEC RenameObject 'Members_PK', 'PK_Members'
EXEC RenameObject 'Containers_FK', 'FK_Containers_Containers'
EXEC RenameObject 'Containers_PK', 'UQ_Containers_EntityId'
EXEC RenameObject 'ACLs_PK', 'UQ_ACLs_ObjectId'
EXEC RenameObject 'Containers_AK', 'UQ_Containers_Parent_Name'
EXEC RenameObject 'Documents_AK', 'UQ_Documents_Parent_DocumentName'
EXEC RenameObject 'Name_AK', 'UQ_Principals_ProjectId_Name'
GO

DROP PROCEDURE RenameObject
GO