/*
 * Copyright (c) 2009 LabKey Corporation
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
 
/* core-0.00-1.30.sql */

-- Create "core" schema, tables, etc.

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

/* core-1.30-1.40.sql */

-- Create a log of events (created, verified, password reset, etc.) associated with users
CREATE TABLE core.UserHistory
(
    UserId USERID,
    Date TIMESTAMP,
    Message VARCHAR(500),

	CONSTRAINT PK_UserHistory PRIMARY KEY (UserId, Date),
	CONSTRAINT FK_UserHistory_UserId FOREIGN KEY (UserId) REFERENCES core.Principals(UserId)
);

/* core-1.40-1.50.sql */

CREATE SCHEMA temp;

/* core-1.50-1.60.sql */

ALTER TABLE core.UsersData ALTER COLUMN Phone TYPE VARCHAR(64);
ALTER TABLE core.UsersData ALTER COLUMN Mobile TYPE VARCHAR(64);
ALTER TABLE core.UsersData ALTER COLUMN Pager TYPE VARCHAR(64);

/* core-1.60-1.70.sql */

ALTER TABLE core.UsersData ADD DisplayName VARCHAR(64) NULL;

DELETE FROM core.UsersData
 WHERE UserID NOT IN
 	(SELECT P1.UserId
  	FROM core.Principals P1
  	WHERE P1.Type = 'u');

UPDATE core.UsersData
SET DisplayName =
	(SELECT Name
		FROM core.Principals P1
		WHERE P1.Type = 'u'
		AND P1.UserId = core.UsersData.UserId
	);

ALTER TABLE core.UsersData ALTER COLUMN DisplayName SET NOT NULL;
ALTER TABLE core.UsersData ADD CONSTRAINT UQ_DisplayName UNIQUE (DisplayName);

CREATE TABLE core.Report
(
    RowId SERIAL,
    ReportKey VARCHAR(255),
    CreatedBy USERID,
    ModifiedBy USERID,
    Created TIMESTAMP,
    Modified TIMESTAMP,
    ContainerId ENTITYID NOT NULL,
    EntityId ENTITYID NULL,
    DescriptorXML TEXT,

    CONSTRAINT PK_Report PRIMARY KEY (RowId)
);

/* core-1.70-2.00.sql */

CREATE OR REPLACE FUNCTION core.fn_dropifexists (text, text, text, text) RETURNS integer AS '
DECLARE
    objname ALIAS FOR $1;
    objschema ALIAS FOR $2;
    objtype ALIAS FOR $3;
    subobjname ALIAS FOR $4;
	ret_code INTEGER;
	fullname text;
	tempschema text;
    BEGIN
    ret_code := 0;
    fullname := (LOWER(objschema)||''.''||LOWER(objname));
	    IF (UPPER(objtype)) = ''TABLE'' THEN
		BEGIN
            IF EXISTS( SELECT * FROM pg_tables WHERE tablename = LOWER(objname) AND schemaname = LOWER(objschema) )
            THEN
                EXECUTE ''DROP TABLE ''||fullname;
                ret_code = 1;
            ELSE
                BEGIN
                    SELECT INTO tempschema schemaname FROM pg_tables WHERE tablename = LOWER(objname) AND schemaname LIKE ''%temp%'';
                    IF (tempschema IS NOT NULL)
                    THEN
                        EXECUTE ''DROP TABLE ''|| tempschema || ''.'' || objname;
                        ret_code = 1;
                    END IF;
                END;
            END IF;
		END;
	    ELSEIF (UPPER(objtype)) = ''VIEW'' THEN
		BEGIN
		    IF EXISTS( SELECT * FROM pg_views WHERE viewname = LOWER(objname) AND schemaname = LOWER(objschema) )
		    THEN
			EXECUTE ''DROP VIEW ''||fullname;
			ret_code = 1;
		    END IF;
		END;
	    ELSEIF (UPPER(objtype)) = ''INDEX'' THEN
		BEGIN
		    fullname := LOWER(objschema) || ''.'' || LOWER(subobjname);
		    IF EXISTS( SELECT * FROM pg_indexes WHERE tablename = LOWER(objname) AND indexname = LOWER(subobjname) AND schemaname = LOWER(objschema) )
		    THEN
			EXECUTE ''DROP INDEX ''|| fullname;
			ret_code = 1;
		    ELSE
			IF EXISTS( SELECT * FROM pg_indexes WHERE indexname = LOWER(subobjname) AND schemaname = LOWER(objschema) )
				THEN RAISE EXCEPTION ''INDEX - % defined on a different table.'', subobjname;
			END IF;
		    END IF;
		END;
	    ELSEIF (UPPER(objtype)) = ''SCHEMA'' THEN
		BEGIN
		    IF EXISTS( SELECT * FROM pg_namespace WHERE nspname = LOWER(objschema))
		    THEN
			IF objname = ''*'' THEN
				EXECUTE ''DROP SCHEMA ''|| LOWER(objschema) || '' CASCADE'';
				ret_code = 1;
			ELSEIF (objname = '''' OR objname IS NULL) THEN
				EXECUTE ''DROP SCHEMA ''|| LOWER(objschema) || '' RESTRICT'';
				ret_code = 1;
			ELSE
				RAISE EXCEPTION ''Invalid objname for objtype of SCHEMA;  must be either "*" (for DROP SCHEMA CASCADE) or NULL (for DROP SCHEMA RESTRICT)'';
			END IF;
		    END IF;
		END;
	    ELSE
		RAISE EXCEPTION ''Invalid object type - %;  Valid values are TABLE, VIEW, INDEX, SCHEMA '', objtype;
	    END IF;

	RETURN ret_code;
	END;
' LANGUAGE plpgsql;


SELECT core.fn_dropifexists ('Containers', 'core', 'Index', 'IX_Containers_Parent_Entity');
SELECT core.fn_dropifexists ('Documents', 'core', 'Index', 'IX_Documents_Container');
SELECT core.fn_dropifexists ('Documents', 'core', 'Index', 'IX_Documents_Parent');

CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId);
ALTER TABLE core.Containers ADD CONSTRAINT UQ_Containers_RowId UNIQUE (RowId);
CREATE INDEX IX_Documents_Container ON core.Documents(Container);
CREATE INDEX IX_Documents_Parent ON core.Documents(Parent);

ALTER TABLE core.Containers ADD
    SortOrder INTEGER NOT NULL DEFAULT 0;

ALTER TABLE core.Report
    ADD COLUMN ReportOwner INT;