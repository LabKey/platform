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
 
-- Create "core" tables, views, etc.

CREATE DOMAIN public.UNIQUEIDENTIFIER AS VARCHAR(36);
CREATE DOMAIN public.ENTITYID AS VARCHAR(36);
CREATE DOMAIN public.USERID AS INT;

CREATE SCHEMA core;
SET search_path TO core, public;

-- for JDBC Login support, validates email/password,
-- UserId is stored in the Principals table
-- LDAP authenticated users are not in this table

CREATE TABLE Logins
	(
	Email VARCHAR(255) NOT NULL,
	Crypt VARCHAR(64) NOT NULL,
	Verification VARCHAR(64),

	CONSTRAINT PK_Logins PRIMARY KEY (Email)
	);

-- Principals is used for managing security related information
-- It is not used for validating login, that requires an 'external'
-- process, either using SMB, LDAP, JDBC etc (see Logins table)
--
-- It does not contain contact info and other generic user visible data

CREATE TABLE Principals
	(
	UserId SERIAL,			        -- user or group
	Container ENTITYID,				-- NULL for all users, NOT NULL for _ALL_ groups
    OwnerId ENTITYID NULL,
	Name VARCHAR(64),				-- email (must contain @ and .), group name (no punctuation), or hidden (no @)
	Type CHAR(1),                   -- 'u'=user 'g'=group (NYI 'r'=role, 'm'=managed(module specific)

	CONSTRAINT PK_Principals PRIMARY KEY (UserId),
    CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId)
	);


SELECT SETVAL('Principals_UserId_Seq', 1000);

-- maps users to groups (issue: groups containing groups?)
CREATE TABLE Members
	(
	UserId USERID,
	GroupId USERID,
	
	CONSTRAINT PK_Members PRIMARY KEY (UserId, GroupId)
	);


CREATE TABLE UsersData
	(
	-- standard fields
	_ts TIMESTAMP DEFAULT now(),
	EntityId ENTITYID NOT NULL,
	CreatedBy USERID,
	Created TIMESTAMP,
	ModifiedBy USERID,
	Modified TIMESTAMP,
	Owner USERID NULL,

	UserId USERID,

	FirstName VARCHAR(64) NULL,
	LastName VARCHAR(64) NULL,
--	Email VARCHAR(128) NULL,  from Principals table
	Phone VARCHAR(24) NULL,
	Mobile VARCHAR(24) NULL,
	Pager VARCHAR(24) NULL,
	IM VARCHAR(64)  NULL,
	Description VARCHAR(255),
	LastLogin TIMESTAMP,

	CONSTRAINT PK_UsersData PRIMARY KEY (UserId)
	);


CREATE TABLE ACLs
	(
	_ts TIMESTAMP DEFAULT now(),

	-- we use UNIQUEIDENTIFIER, so we don't need to know ahead of time, what ACLs will be used for
	Container UNIQUEIDENTIFIER,
	ObjectId UNIQUEIDENTIFIER,
	ACL BYTEA,
	
	CONSTRAINT UQ_ACLs_ContainerObjectId UNIQUE (Container, ObjectId)
	);


CREATE TABLE Containers
	(
	_ts TIMESTAMP DEFAULT now(),
	RowId SERIAL,
	EntityId ENTITYID NOT NULL,
	CreatedBy USERID,
	Created TIMESTAMP,

	Parent ENTITYID,
	Name VARCHAR(255),

	CONSTRAINT UQ_Containers_EntityId UNIQUE (EntityId),
	CONSTRAINT UQ_Containers_Parent_Name UNIQUE (Parent, Name),
	CONSTRAINT FK_Containers_Containers FOREIGN KEY (Parent) REFERENCES Containers(EntityId)
	);


-- table for all modules
CREATE TABLE Modules
	(
	Name VARCHAR(255),
	ClassName VARCHAR(255),
	InstalledVersion FLOAT8,
	Enabled BOOLEAN DEFAULT '1',

	CONSTRAINT PK_Modules PRIMARY KEY (Name)
	);


-- keep track of sql scripts that have been run in each module
CREATE TABLE SqlScripts
	(
	-- standard fields
	_ts TIMESTAMP DEFAULT now(),
	CreatedBy USERID,
	Created TIMESTAMP,
	ModifiedBy USERID,
	Modified TIMESTAMP,

	ModuleName VARCHAR(100),
	FileName VARCHAR(300),

	CONSTRAINT PK_SqlScripts PRIMARY KEY (ModuleName, FileName)
	);


CREATE OR REPLACE VIEW Users AS
	SELECT Name AS Email, UsersData.*
	FROM Principals Principals LEFT OUTER JOIN UsersData ON Principals.UserId = UsersData.UserId
	WHERE Type = 'u';


CREATE OR REPLACE RULE Users_Update AS
	ON UPDATE TO Users DO INSTEAD
		UPDATE UsersData SET
			ModifiedBy = NEW.ModifiedBy,
			Modified = NEW.Modified,
			FirstName = NEW.FirstName,
			LastName = NEW.LastName,
			Phone = NEW.Phone,
			Mobile = NEW.Mobile,
			Pager = NEW.Pager,
			IM = NEW.IM,
			Description = NEW.Description,
			LastLogin = NEW.LastLogin
		WHERE UserId = NEW.UserId;


-- generic table for all attached docs
CREATE TABLE Documents
	(
	-- standard fields
	_ts TIMESTAMP DEFAULT now(),
	RowId SERIAL,
	CreatedBy USERID,
	Created TIMESTAMP,
	ModifiedBy USERID,
	Modified TIMESTAMP,
	Owner USERID NULL,

	Container ENTITYID NOT NULL,	-- Container of parent, if parent has no ACLs
	Parent ENTITYID NOT NULL,
	DocumentName VARCHAR(195),	--filename

	DocumentSize INT DEFAULT -1,
	DocumentType VARCHAR(32) DEFAULT 'text/plain',
	Document BYTEA,			-- ContentType LIKE application/*

	CONSTRAINT PK_Documents PRIMARY KEY (RowId),
	CONSTRAINT UQ_Documents_Parent_DocumentName UNIQUE (Parent, DocumentName)
	);

CREATE VIEW core.Contacts As
	SELECT Users.FirstName || ' ' || Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Container, Principals.Name AS GroupName
	FROM core.Principals Principals
	    INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
	    INNER JOIN core.Users Users ON Members.UserId = Users.UserId;
