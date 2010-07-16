/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
 
/* core-0.00-2.00.sql */

/* core-0.00-1.30.sql */

-- Create "core" schema, tables, etc.

CREATE DOMAIN public.UNIQUEIDENTIFIER AS VARCHAR(36);
CREATE DOMAIN public.ENTITYID AS VARCHAR(36);
CREATE DOMAIN public.USERID AS INT;

CREATE SCHEMA core;

-- for JDBC Login support, validates email/password,
-- UserId is stored in the Principals table
-- LDAP authenticated users are not in this table

CREATE TABLE core.Logins
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

CREATE TABLE core.Principals
(
	UserId SERIAL,			        -- user or group
	Container ENTITYID,				-- NULL for all users, NOT NULL for _ALL_ groups
    OwnerId ENTITYID NULL,
	Name VARCHAR(64),				-- email (must contain @ and .), group name (no punctuation), or hidden (no @)
	Type CHAR(1),                   -- 'u'=user 'g'=group (NYI 'r'=role, 'm'=managed(module specific)

	CONSTRAINT PK_Principals PRIMARY KEY (UserId),
    CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId)
);


SELECT SETVAL('core.Principals_UserId_Seq', 1000);

-- maps users to groups (issue: groups containing groups?)
CREATE TABLE core.Members
(
	UserId USERID,
	GroupId USERID,
	
	CONSTRAINT PK_Members PRIMARY KEY (UserId, GroupId)
);


CREATE TABLE core.UsersData
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

    DisplayName VARCHAR(64) NOT NULL,
	FirstName VARCHAR(64) NULL,
	LastName VARCHAR(64) NULL,
	Phone VARCHAR(64) NULL,
	Mobile VARCHAR(64) NULL,
	Pager VARCHAR(64) NULL,
	IM VARCHAR(64)  NULL,
	Description VARCHAR(255),
	LastLogin TIMESTAMP,

	CONSTRAINT PK_UsersData PRIMARY KEY (UserId),
	CONSTRAINT UQ_DisplayName UNIQUE (DisplayName)
);


CREATE TABLE core.ACLs
(
	_ts TIMESTAMP DEFAULT now(),

	-- we use UNIQUEIDENTIFIER, so we don't need to know ahead of time, what ACLs will be used for
	Container UNIQUEIDENTIFIER,
	ObjectId UNIQUEIDENTIFIER,
	ACL BYTEA,
	
	CONSTRAINT UQ_ACLs_ContainerObjectId UNIQUE (Container, ObjectId)
);


CREATE TABLE core.Containers
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
	CONSTRAINT FK_Containers_Containers FOREIGN KEY (Parent) REFERENCES core.Containers(EntityId)
);


-- table for all modules
CREATE TABLE core.Modules
(
	Name VARCHAR(255),
	ClassName VARCHAR(255),
	InstalledVersion FLOAT8,
	Enabled BOOLEAN DEFAULT '1',

	CONSTRAINT PK_Modules PRIMARY KEY (Name)
);


-- keep track of sql scripts that have been run in each module
CREATE TABLE core.SqlScripts
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
CREATE TABLE core.Documents
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

CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId);
ALTER TABLE core.Containers ADD CONSTRAINT UQ_Containers_RowId UNIQUE (RowId);
CREATE INDEX IX_Documents_Container ON core.Documents(Container);
CREATE INDEX IX_Documents_Parent ON core.Documents(Parent);

ALTER TABLE core.Containers ADD
    SortOrder INTEGER NOT NULL DEFAULT 0;

ALTER TABLE core.Report
    ADD COLUMN ReportOwner INT;

/* core-2.00-2.10.sql */

ALTER TABLE core.Containers ADD 
    CaBIGPublished BOOLEAN NOT NULL DEFAULT '0';

CREATE TABLE core.ContainerAliases
(
	Path VARCHAR(255) NOT NULL,
	ContainerId ENTITYID NOT NULL,

	CONSTRAINT UK_ContainerAliases_Paths UNIQUE (Path),
	CONSTRAINT FK_ContainerAliases_Containers FOREIGN KEY (ContainerId) REFERENCES core.Containers(EntityId)
);

/* core-2.10-2.20.sql */

CREATE TABLE core.MappedDirectories
(
   EntityId ENTITYID NOT NULL,
   Container ENTITYID NOT NULL,
   Relative BOOLEAN NOT NULL,
   Name VARCHAR(80),
   Path VARCHAR(255),
   CONSTRAINT PK_MappedDirecctories PRIMARY KEY (EntityId),
   CONSTRAINT UQ_MappedDirectories UNIQUE (Container,Name)
);

/* core-2.20-2.30.sql */

-- Add ability to drop constraints; switch to $$ quoting for sanity
CREATE FUNCTION core.fn_dropifexists (text, text, text, text) RETURNS integer AS $$
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
    fullname := (LOWER(objschema) || '.' || LOWER(objname));
	    IF (UPPER(objtype)) = 'TABLE' THEN
		BEGIN
            IF EXISTS( SELECT * FROM pg_tables WHERE tablename = LOWER(objname) AND schemaname = LOWER(objschema) )
            THEN
                EXECUTE 'DROP TABLE ' || fullname;
                ret_code = 1;
            ELSE
                BEGIN
                    SELECT INTO tempschema schemaname FROM pg_tables WHERE tablename = LOWER(objname) AND schemaname LIKE '%temp%';
                    IF (tempschema IS NOT NULL)
                    THEN
                        EXECUTE 'DROP TABLE ' || tempschema || '.' || objname;
                        ret_code = 1;
                    END IF;
                END;
            END IF;
		END;
	    ELSEIF (UPPER(objtype)) = 'VIEW' THEN
		BEGIN
		    IF EXISTS( SELECT * FROM pg_views WHERE viewname = LOWER(objname) AND schemaname = LOWER(objschema) )
		    THEN
			EXECUTE 'DROP VIEW ' || fullname;
			ret_code = 1;
		    END IF;
		END;
	    ELSEIF (UPPER(objtype)) = 'INDEX' THEN
		BEGIN
		    fullname := LOWER(objschema) || '.' || LOWER(subobjname);
		    IF EXISTS( SELECT * FROM pg_indexes WHERE tablename = LOWER(objname) AND indexname = LOWER(subobjname) AND schemaname = LOWER(objschema) )
		    THEN
			EXECUTE 'DROP INDEX ' || fullname;
			ret_code = 1;
		    ELSE
			IF EXISTS( SELECT * FROM pg_indexes WHERE indexname = LOWER(subobjname) AND schemaname = LOWER(objschema) )
				THEN RAISE EXCEPTION 'INDEX - % defined on a different table.', subobjname;
			END IF;
		    END IF;
		END;
	    ELSEIF (UPPER(objtype)) = 'CONSTRAINT' THEN
		BEGIN
		    IF EXISTS( SELECT * FROM pg_class LEFT JOIN pg_constraint ON conrelid = pg_class.oid INNER JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace
                WHERE relkind = 'r' AND contype IS NOT NULL AND nspname = LOWER(objschema) AND relname = LOWER(objname) AND conname = LOWER(subobjname) )
		    THEN
                EXECUTE 'ALTER TABLE ' || fullname || ' DROP CONSTRAINT ' || subobjname;
                ret_code = 1;
		    END IF;
		END;
	    ELSEIF (UPPER(objtype)) = 'SCHEMA' THEN
		BEGIN
		    IF EXISTS( SELECT * FROM pg_namespace WHERE nspname = LOWER(objschema))
		    THEN
			IF objname = '*' THEN
				EXECUTE 'DROP SCHEMA ' || LOWER(objschema) || ' CASCADE';
				ret_code = 1;
			ELSEIF (objname = '' OR objname IS NULL) THEN
				EXECUTE 'DROP SCHEMA ' || LOWER(objschema) || ' RESTRICT';
				ret_code = 1;
			ELSE
				RAISE EXCEPTION 'Invalid objname for objtype of SCHEMA;  must be either "*" (for DROP SCHEMA CASCADE) or NULL (for DROP SCHEMA RESTRICT)';
			END IF;
		    END IF;
		END;
	    ELSE
		RAISE EXCEPTION 'Invalid object type - %;  Valid values are TABLE, VIEW, INDEX, CONSTRAINT, SCHEMA ', objtype;
	    END IF;

	RETURN ret_code;
	END;
$$ LANGUAGE plpgsql;

/* Expand DocumentType to handle new longer Office document mime-types */
ALTER TABLE core.Documents ALTER COLUMN DocumentType TYPE VARCHAR(500);