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
-- Table for all modules
CREATE TABLE Modules
    (
    Name NVARCHAR(255),
    ClassName NVARCHAR(255),
    InstalledVersion FLOAT,
    Enabled BIT DEFAULT 1,

    CONSTRAINT Modules_PK PRIMARY KEY (Name)
    )
GO


-- Table to keep track of SQL scripts that have been run on a given installation
CREATE TABLE SqlScripts
	(
	-- standard fields
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,

	ModuleName NVARCHAR(100),
	FileName NVARCHAR(300),

	CONSTRAINT SqlScripts_PK PRIMARY KEY (ModuleName, FileName)
	)
GO

