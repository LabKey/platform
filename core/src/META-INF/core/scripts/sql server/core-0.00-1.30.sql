/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

EXEC sp_addtype 'ENTITYID', 'UNIQUEIDENTIFIER'
EXEC sp_addtype 'USERID', 'INT'
GO


EXEC sp_addapprole 'core', 'password'
GO


-- for JDBC Login support, validates email/password,
-- UserId is stored in the Principals table
-- LDAP authenticated users are not in this table

CREATE TABLE core.Logins
	(
	Email VARCHAR(255) NOT NULL,
	Crypt VARCHAR(64) NOT NULL,
	Verification VARCHAR(64),

	CONSTRAINT PK_Logins PRIMARY KEY (Email),
	)
GO


-- Principals is used for managing security related information
-- It is not used for validating login, that requires an 'external'
-- process, either using SMB, LDAP, JDBC etc (see Logins table)
--
-- It does not contain contact info and other generic user visible data

CREATE TABLE core.Principals
	(
	UserId USERID IDENTITY(1000,1),	-- user or group
	Container ENTITYID,				-- NULL for all users, NOT NULL for _ALL_ groups
    OwnerId ENTITYID NULL,
	Name NVARCHAR(64),				-- email (must contain @ and .), group name (no punctuation), or hidden (no @)
	Type CHAR(1),                   -- 'u'=user 'g'=group (NYI 'r'=role, 'm'=managed(module specific)

	CONSTRAINT PK_Principals PRIMARY KEY (UserId),
	CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId)
	)
GO


-- maps users to groups
CREATE TABLE core.Members
	(
	UserId USERID,
	GroupId USERID,
	
	CONSTRAINT PK_Members PRIMARY KEY (UserId, GroupId)
	)
GO


CREATE TABLE core.UsersData
	(
	-- standard fields
	_ts TIMESTAMP,
	EntityId ENTITYID DEFAULT NEWID(),
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,
	Owner USERID NULL,

	UserId USERID,

	FirstName NVARCHAR(64) NULL,
	LastName NVARCHAR(64) NULL,
--	Email NVARCHAR(128) NULL,  from Principals table
	Phone NVARCHAR(24) NULL,
	Mobile NVARCHAR(24) NULL,
	Pager NVARCHAR(24) NULL,
	IM NVARCHAR(64)  NULL,
	Description NVARCHAR(255),
	LastLogin DATETIME,

	CONSTRAINT PK_UsersData PRIMARY KEY (UserId),
	)
GO


CREATE TABLE core.ACLs
	(
	_ts TIMESTAMP,

	Container UNIQUEIDENTIFIER,
	ObjectId UNIQUEIDENTIFIER,
	ACL	VARBINARY(256),
	
	CONSTRAINT UQ_ACLs_ContainerObjectId UNIQUE (Container, ObjectId),
	)
GO


CREATE TABLE core.Containers
	(
	_ts TIMESTAMP,
	RowId INT IDENTITY(1,1),
	EntityId ENTITYID DEFAULT NEWID(),
	CreatedBy USERID,
	Created DATETIME,

	Parent ENTITYID,
	Name VARCHAR(255),

	CONSTRAINT UQ_Containers_EntityId UNIQUE (EntityId),
	CONSTRAINT UQ_Containers_Parent_Name UNIQUE (Parent, Name),
	CONSTRAINT FK_Containers_Containers FOREIGN KEY (Parent) REFERENCES core.Containers(EntityId)
	)
GO


-- table for all modules
CREATE TABLE core.Modules
	(
	Name NVARCHAR(255),
	ClassName NVARCHAR(255),
	InstalledVersion FLOAT,
	Enabled BIT DEFAULT 1,

	CONSTRAINT PK_Modules PRIMARY KEY (Name)
	)
GO


-- keep track of sql scripts that have been run in each module
CREATE TABLE core.SqlScripts
	(
	-- standard fields
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,

	ModuleName NVARCHAR(100),
	FileName NVARCHAR(300),

	CONSTRAINT PK_SqlScripts PRIMARY KEY (ModuleName, FileName)
	)
GO


CREATE VIEW core.Users AS
	SELECT Principals.Name Email, UsersData.*
	FROM core.Principals Principals INNER JOIN
		core.UsersData UsersData ON Principals.UserId = UsersData.UserId
	WHERE Type = 'u'
GO


-- generic table for all attached docs
CREATE TABLE core.Documents
	(
	-- standard fields
	_ts TIMESTAMP,
	RowId INT IDENTITY(1,1),
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,
	Owner USERID NULL,

	Container ENTITYID NOT NULL, -- Container of parent, if parent has no ACLs
	Parent ENTITYID NOT NULL,
	DocumentName NVARCHAR(195),		--filename

	DocumentSize INT DEFAULT -1,
	DocumentType VARCHAR(32) DEFAULT 'text/plain',
	Document IMAGE,			-- ContentType LIKE application/*

	CONSTRAINT PK_Documents PRIMARY KEY (RowId),
	CONSTRAINT UQ_Documents_Parent_DocumentName UNIQUE (Parent, DocumentName)
	)
GO

CREATE VIEW core.Contacts As
	SELECT Users.FirstName + ' ' + Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Container, Principals.Name AS GroupName
	FROM core.Principals Principals
		INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
		INNER JOIN core.Users Users ON Members.UserId = Users.UserId
go
