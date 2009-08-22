/*
 * Copyright (c) 2005 Fred Hutchinson Cancer Research Center
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

EXEC ChangeBogusIndexName 'dbo', 'PK', 'TestTable'
GO

DROP PROCEDURE ChangeBogusIndexName
GO

-- Rename an object, failing silently
CREATE PROCEDURE RenameObject(@oldName AS VARCHAR(200), @newName AS VARCHAR(200)) AS
    IF OBJECT_ID(@oldName) IS NOT NULL
        EXEC sp_rename @oldName, @newName
GO

-- Rename PKs, FKs & AKs
EXEC RenameObject 'PropertySet_PK', 'PK_PropertySet'
EXEC RenameObject 'Properties_PK', 'PK_Properties'
EXEC RenameObject 'PortalWebParts_PK', 'PK_PortalWebParts'
EXEC RenameObject 'Announcements_PK', 'PK_Announcements'
EXEC RenameObject 'Pages_PK', 'PK_Pages'
EXEC RenameObject 'Projects_PK', 'PK_Projects'
EXEC RenameObject 'Timesheet_PK', 'PK_Timesheet'
EXEC RenameObject 'StatusFiles_PK', 'PK_StatusFiles'
EXEC RenameObject 'Issues_PK', 'PK_Issues'
EXEC RenameObject 'Comments_PK', 'PK_Comments'
EXEC RenameObject 'IssueKeywords_PK', 'PK_IssueKeywords'
EXEC RenameObject 'Comments_FK', 'FK_Comments_Issues'
EXEC RenameObject 'PropertySet_AK', 'UQ_PropertySet'
EXEC RenameObject 'Announcements_AK', 'UQ_Announcements'
EXEC RenameObject 'Pages_AK', 'UQ_Pages'
EXEC RenameObject 'Projects_AK', 'UQ_Projects'

DROP PROCEDURE RenameObject
GO