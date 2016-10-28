/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
 
/* core-0.00-12.30.sql */

CREATE DOMAIN public.UNIQUEIDENTIFIER AS VARCHAR(36);
CREATE DOMAIN public.ENTITYID AS VARCHAR(36);
CREATE DOMAIN public.USERID AS INT;

CREATE SCHEMA core;
CREATE SCHEMA temp;

-- for JDBC Login support, validates email/password,
-- UserId is stored in the Principals table
-- LDAP authenticated users are not in this table

CREATE TABLE core.Logins
(
    Email VARCHAR(255) NOT NULL,
    Crypt VARCHAR(64) NOT NULL,
    Verification VARCHAR(64),
    LastChanged TIMESTAMP NULL,
    PreviousCrypts VARCHAR(1000),

    CONSTRAINT PK_Logins PRIMARY KEY (Email)
);

-- Principals is used for managing security related information
-- It is not used for validating login, that requires an 'external'
-- process, either using LDAP, JDBC, etc. (see Logins table)
--
-- It does not contain contact info or other generic user visible data

CREATE TABLE core.Principals
(
    UserId SERIAL,                    -- user or group
    Container ENTITYID,               -- NULL for all users, NOT NULL for _ALL_ groups
    OwnerId ENTITYID NULL,
    Name VARCHAR(64),                 -- email (must contain @ and .), group name (no punctuation), or hidden (no @)
    Type CHAR(1),                     -- 'u'=user 'g'=group (NYI 'r'=role, 'm'=managed(module specific)
    Active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT PK_Principals PRIMARY KEY (UserId),
    CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId)
);

SELECT SETVAL('core.Principals_UserId_Seq', 1000);

-- maps users to groups
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

CREATE TABLE core.Containers
(
    _ts TIMESTAMP DEFAULT now(),
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,

    Parent ENTITYID,
    Name VARCHAR(255),
    SortOrder INTEGER NOT NULL DEFAULT 0,
    Searchable BOOLEAN NOT NULL DEFAULT TRUE,

    Description VARCHAR(4000),
    Title VARCHAR(1000),
    Type VARCHAR(16) NOT NULL DEFAULT 'normal',

    CONSTRAINT UQ_Containers_RowId UNIQUE (RowId),
    CONSTRAINT UQ_Containers_EntityId UNIQUE (EntityId),
    CONSTRAINT UQ_Containers_Parent_Name UNIQUE (Parent, Name),
    CONSTRAINT FK_Containers_Containers FOREIGN KEY (Parent) REFERENCES core.Containers(EntityId)
);

CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId);

-- table for all modules
CREATE TABLE core.Modules
(
    Name VARCHAR(255),
    ClassName VARCHAR(255),
    InstalledVersion FLOAT8,
    Enabled BOOLEAN DEFAULT '1',
    AutoUninstall BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE means LabKey should uninstall this module (drop schemas, delete SqlScripts rows, delete Modules rows), if it no longer exists
    Schemas VARCHAR(100) NULL,                     -- Schemas managed by this module; LabKey will drop these schemas when a module marked AutoUninstall = TRUE is missing

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

    Container ENTITYID NOT NULL,    -- Container of parent, if parent has no ACLs
    Parent ENTITYID NOT NULL,
    DocumentName VARCHAR(195),    --filename

    DocumentSize INT DEFAULT -1,
    DocumentType VARCHAR(500) DEFAULT 'text/plain',    -- Needs to be large enough to handle new Office document mime-types
    Document BYTEA,            -- ContentType LIKE application/*

    LastIndexed TIMESTAMP NULL,

    CONSTRAINT PK_Documents PRIMARY KEY (RowId),
    CONSTRAINT UQ_Documents_Parent_DocumentName UNIQUE (Parent, DocumentName)
);

CREATE INDEX IX_Documents_Container ON core.Documents(Container);
CREATE INDEX IX_Documents_Parent ON core.Documents(Parent);

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
    ReportOwner INT,
    Flags INT NOT NULL DEFAULT 0,
    CategoryId Integer,
    DisplayOrder Integer NOT NULL DEFAULT 0,

    CONSTRAINT PK_Report PRIMARY KEY (RowId)
);

CREATE TABLE core.ContainerAliases
(
    Path VARCHAR(255) NOT NULL,
    ContainerId ENTITYID NOT NULL,

    CONSTRAINT UK_ContainerAliases_Paths UNIQUE (Path),
    CONSTRAINT FK_ContainerAliases_Containers FOREIGN KEY (ContainerId) REFERENCES core.Containers(EntityId)
);

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

CREATE TABLE core.Policies
(
    ResourceId ENTITYID NOT NULL,
    ResourceClass VARCHAR(1000),
    Container ENTITYID NOT NULL,
    Modified TIMESTAMP NOT NULL,

    CONSTRAINT PK_Policies PRIMARY KEY(ResourceId)
);

CREATE TABLE core.RoleAssignments
(
    ResourceId ENTITYID NOT NULL,
    UserId USERID NOT NULL,
    Role VARCHAR(500) NOT NULL,

    CONSTRAINT PK_RoleAssignments PRIMARY KEY(ResourceId,UserId,Role),
    CONSTRAINT FK_RA_P FOREIGN KEY(ResourceId) REFERENCES core.Policies(ResourceId),
    CONSTRAINT FK_RA_UP FOREIGN KEY(UserId) REFERENCES core.Principals(UserId)
);

CREATE TABLE core.MvIndicators
(
    Container ENTITYID,
    MvIndicator VARCHAR(64),
    Label VARCHAR(255) NULL,

    CONSTRAINT PK_MvIndicators_Container_MvIndicator PRIMARY KEY (Container, MvIndicator)
);

-- CONSIDER: eventually switch to entityid PK/FK
CREATE TABLE core.PortalPages
(
    EntityId ENTITYID NOT NULL,
    Container ENTITYID NOT NULL,
    PageId VARCHAR(50) NOT NULL,
    Index INTEGER NOT NULL DEFAULT 0,
    Caption VARCHAR(64),
    Hidden BOOLEAN NOT NULL DEFAULT false,
    Type VARCHAR(20), -- 'portal', 'folder', 'action'
    -- associate page with a registered folder type
    -- folderType varchar(64),
    Action VARCHAR(200),    -- type='action' see DetailsURL
    TargetFolder ENTITYID,  -- type=='folder'
    Permanent BOOLEAN NOT NULL DEFAULT false, -- may not be renamed,hidden,deleted (w/o changing folder type)
    Properties TEXT,

    CONSTRAINT PK_PortalPages PRIMARY KEY (Container, PageId),
    CONSTRAINT FK_PortalPages_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);

CLUSTER PK_PortalPages ON core.PortalPages;
CREATE INDEX IX_PortalPages_EntityId ON core.PortalPages(EntityId);

CREATE TABLE core.PortalWebParts
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    PageId VARCHAR(50) NOT NULL,
    Index INT NOT NULL,
    Name VARCHAR(64),
    Location VARCHAR(16),    -- 'body', 'left', 'right'
    Properties TEXT,    -- url encoded properties
    Permanent BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_PortalWebParts PRIMARY KEY (RowId),
    CONSTRAINT FK_PortalWebParts_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_PortalWebPartPages FOREIGN KEY (Container, PageId) REFERENCES core.PortalPages (Container, PageId)
);

CREATE INDEX IX_PortalWebParts ON core.PortalWebParts(Container);
CLUSTER IX_PortalWebParts ON core.PortalWebParts;

-- represents a grouping category for views (reports etc.)
CREATE TABLE core.ViewCategory
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP DEFAULT now(),
    ModifiedBy USERID,
    Modified TIMESTAMP DEFAULT now(),

    Label VARCHAR(200) NOT NULL,
    DisplayOrder Integer NOT NULL DEFAULT 0,

    CONSTRAINT PK_ViewCategory PRIMARY KEY (RowId),
    CONSTRAINT UQ_Container_Label UNIQUE (Container, Label)
);

-- This empty stored procedure doesn't directly change the database, but calling it from a sql script signals the
-- script runner to invoke the specified method at this point in the script running process.  See usages of the
-- UpgradeCode interface for more details.
CREATE FUNCTION core.executeJavaUpgradeCode(text) RETURNS void AS $$
    DECLARE note TEXT := 'Empty function that signals script runner to execute Java code.  See usages of UpgradeCode.java.';
    BEGIN
    END
$$ LANGUAGE plpgsql;

-- Use to drop TABLE, VIEW, INDEX, CONSTRAINT, DEFAULT, or SCHEMA if it exists
CREATE FUNCTION core.fn_dropifexists (text, text, text, text) RETURNS INTEGER AS $$
DECLARE
    objname ALIAS FOR $1;
    objschema ALIAS FOR $2;
    objtype ALIAS FOR $3;
    subobjname ALIAS FOR $4;
    ret_code INTEGER;
    fullname TEXT;
    tempschema TEXT;
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
        ELSEIF (UPPER(objtype)) = 'DEFAULT' THEN
        BEGIN
            EXECUTE 'ALTER TABLE ' || fullname || ' ALTER COLUMN ' || subobjname || ' DROP DEFAULT';
            ret_code = 1;
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

CREATE AGGREGATE core.array_accum (anyelement)
(
    sfunc = array_append,
    stype = anyarray,
    initcond = '{}'
);

CREATE FUNCTION core.sort(anyarray)
RETURNS anyarray AS $$
SELECT ARRAY(SELECT $1[i] from generate_series(array_lower($1,1),
array_upper($1,1)) g(i) ORDER BY 1)
$$ LANGUAGE SQL STRICT IMMUTABLE;

CREATE AGGREGATE core.array_accum(text) (
    SFUNC = array_append,
    STYPE = text[],
    INITCOND = '{}',
    SORTOP = >
);

/* core-12.30-13.10.sql */

ALTER TABLE core.viewcategory
    ADD COLUMN Parent int4,
    ADD CONSTRAINT fk_viewcategory_parent FOREIGN KEY (rowid) REFERENCES core.viewcategory(rowid) ON DELETE CASCADE;

ALTER TABLE core.ViewCategory DROP CONSTRAINT uq_container_label;
ALTER TABLE core.ViewCategory ADD CONSTRAINT uq_container_label_parent UNIQUE (Container, Label, Parent);

ALTER TABLE core.portalwebparts
  ADD COLUMN permission character varying(256),
  ADD COLUMN permissioncontainer entityid,
  ADD CONSTRAINT fk_portalwebparts_permissioncontainer FOREIGN KEY (permissioncontainer)
      REFERENCES core.containers (entityid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE core.portalwebparts
  DROP CONSTRAINT fk_portalwebparts_permissioncontainer;

ALTER TABLE core.portalwebparts
  ADD CONSTRAINT fk_portalwebparts_permissioncontainer FOREIGN KEY (permissioncontainer)
    REFERENCES core.containers (entityid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE SET NULL;

-- clean up orphaned categories
DELETE FROM core.viewcategory WHERE parent IN
	(SELECT vcp.parent FROM (SELECT DISTINCT parent FROM core.viewcategory WHERE parent IS NOT NULL) vcp LEFT JOIN core.viewcategory vc ON vcp.parent = vc.rowid WHERE rowid IS NULL);

-- correct the fk constraint
ALTER TABLE core.viewcategory DROP CONSTRAINT fk_viewcategory_parent;
ALTER TABLE core.viewcategory ADD CONSTRAINT fk_viewcategory_parent FOREIGN KEY (parent) REFERENCES core.viewcategory(rowid);

/* core-13.10-13.20.sql */

CREATE TABLE core.DbSequences
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(500) NOT NULL,
    Id INTEGER NOT NULL,
    Value BIGINT NOT NULL,

    CONSTRAINT PK_DbSequences PRIMARY KEY (RowId),
    CONSTRAINT UQ_DbSequences_Container_Name_Id UNIQUE (Container, Name, Id)
);

SELECT core.fn_dropifexists ('PortalPages', 'core', 'DEFAULT', 'Index');
ALTER TABLE core.PortalPages ADD CONSTRAINT UQ_PortalPage UNIQUE (Container, Index);

/* core-13.20-13.30.sql */

-- Support longer lists of module schemas (which now may include data source prefixes)
ALTER TABLE core.Modules
    ALTER COLUMN Schemas TYPE VARCHAR(4000);

/* core-13.30-14.10.sql */

CREATE TABLE core.ShortURL
(
    RowId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    ShortURL VARCHAR(255) NOT NULL,
    FullURL VARCHAR(4000) NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_ShortURL PRIMARY KEY (RowId),
    CONSTRAINT UQ_ShortURL_EntityId UNIQUE (EntityId),
    CONSTRAINT UQ_ShortURL_ShortURL UNIQUE (ShortURL)
);