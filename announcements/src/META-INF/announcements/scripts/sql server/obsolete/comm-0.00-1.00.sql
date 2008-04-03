/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

-- Create schema comm: tables for Announcements and Wiki

EXEC sp_addapprole 'comm', 'password'
GO


CREATE TABLE comm.Announcements
	(
	RowId INT IDENTITY(1,1) NOT NULL,
	EntityId ENTITYID NOT NULL,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,
	Owner USERID,
	Container ENTITYID NOT NULL,
	Parent ENTITYID,
	Title NVARCHAR(255),
	Expires DATETIME,
	Body NTEXT,

	CONSTRAINT PK_Announcements PRIMARY KEY (RowId),
	CONSTRAINT UQ_Announcements UNIQUE CLUSTERED (Container, Parent, RowId)
	)
GO


CREATE TABLE comm.Pages 
	(
	RowId INT IDENTITY(1,1) NOT NULL,
	EntityId ENTITYID NOT NULL,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,
	Owner USERID,
	Container ENTITYID NOT NULL,
	Name NVARCHAR(255) NOT NULL,
	Title NVARCHAR(255),
	Body NTEXT

	CONSTRAINT PK_Pages PRIMARY KEY (EntityId),
	CONSTRAINT UQ_Pages UNIQUE CLUSTERED (Container, Name)
	)
GO
