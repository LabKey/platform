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