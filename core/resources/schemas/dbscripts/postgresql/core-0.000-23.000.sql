/*
 * Copyright (c) 2019 LabKey Corporation
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
  RequestedEmail VARCHAR(255),
  VerificationTimeout TIMESTAMP,

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
  ExpirationDate TIMESTAMP,

  CONSTRAINT PK_UsersData PRIMARY KEY (UserId),
  CONSTRAINT UQ_DisplayName UNIQUE (DisplayName)
);

/* 22.xxx SQL scripts */

ALTER TABLE core.UsersData ADD System BOOLEAN NOT NULL DEFAULT FALSE;

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

ALTER TABLE core.Containers ADD LockState VARCHAR(25) NULL;
ALTER TABLE core.Containers ADD ExpirationDate TIMESTAMP NULL;

-- table for all modules
CREATE TABLE core.Modules
(
  Name VARCHAR(255),
  ClassName VARCHAR(255),
  SchemaVersion FLOAT8 NULL,
  Enabled BOOLEAN DEFAULT '1',
  AutoUninstall BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE means LabKey should uninstall this module (drop schemas, delete SqlScripts rows, delete Modules rows), if it no longer exists
  Schemas VARCHAR(4000) NULL,                    -- Schemas managed by this module; LabKey will drop these schemas when a module marked AutoUninstall = TRUE is missing

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
  ContentModified TIMESTAMP NOT NULL,

  CONSTRAINT PK_Report PRIMARY KEY (RowId),
  CONSTRAINT FK_Report_ContainerId FOREIGN KEY (ContainerId) REFERENCES core.Containers (EntityId)
);

CREATE INDEX IDX_Report_ContainerId ON core.Report(ContainerId);

CREATE TABLE core.ContainerAliases
(
  Path VARCHAR(255) NOT NULL,
  ContainerId ENTITYID NOT NULL,

  CONSTRAINT UK_ContainerAliases_Paths UNIQUE (Path),
  CONSTRAINT FK_ContainerAliases_Containers FOREIGN KEY (ContainerId) REFERENCES core.Containers(EntityId)
);

ALTER TABLE core.containeraliases ALTER COLUMN path TYPE VARCHAR(4000);

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
  Index INTEGER NOT NULL,
  Caption VARCHAR(64),
  Hidden BOOLEAN NOT NULL DEFAULT false,
  Type VARCHAR(20), -- 'portal', 'folder', 'action'
  -- associate page with a registered folder type
  -- folderType varchar(64),
  Action VARCHAR(200),    -- type='action' see DetailsURL
  TargetFolder ENTITYID,  -- type=='folder'
  Permanent BOOLEAN NOT NULL DEFAULT false, -- may not be renamed,hidden,deleted (w/o changing folder type)
  Properties TEXT,

  CONSTRAINT FK_PortalPages_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);

-- We don't normally designate a column as "PRIMARY KEY", since it generates an unpredictable PK name. But all existing
-- deployments already have this, so keep it for consistency
ALTER TABLE core.portalpages ADD COLUMN rowId SERIAL PRIMARY KEY;

CREATE INDEX IX_PortalPages_EntityId ON core.PortalPages(EntityId);

CREATE TABLE core.PortalWebParts
(
  RowId SERIAL NOT NULL,
  Container ENTITYID NOT NULL,
  Index INT NOT NULL,
  Name VARCHAR(64),
  Location VARCHAR(16),    -- 'body', 'left', 'right'
  Properties TEXT,    -- url encoded properties
  Permanent BOOLEAN NOT NULL DEFAULT FALSE,
  Permission VARCHAR(256),
  PermissionContainer ENTITYID,
  PortalPageId INTEGER NOT NULL,

  CONSTRAINT PK_PortalWebParts PRIMARY KEY (RowId),
  CONSTRAINT FK_PortalWebParts_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
  CONSTRAINT fk_portalwebpartpages FOREIGN KEY (PortalPageId) REFERENCES core.portalpages (RowId),
  CONSTRAINT FK_PortalWebParts_PermissionContainer FOREIGN KEY (PermissionContainer) REFERENCES core.containers (EntityId) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE SET NULL
);

CREATE INDEX IX_PortalWebParts ON core.PortalWebParts(Container);
CLUSTER IX_PortalWebParts ON core.PortalWebParts;

-- represents a grouping category for reports and datasets
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

  Parent INTEGER,

  CONSTRAINT PK_ViewCategory PRIMARY KEY (RowId),
  CONSTRAINT FK_ViewCategory_Parent FOREIGN KEY (Parent) REFERENCES core.ViewCategory(RowId)
);

-- Make unique index case-insensitive and treat null as a unique value, see #21698
CREATE UNIQUE INDEX uq_container_label_parent ON core.ViewCategory (Container, LOWER(Label), COALESCE(Parent, -1));

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

CREATE TABLE core.Notifications
(
  RowId SERIAL,
  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created TIMESTAMP,

  UserId USERID NOT NULL,
  ObjectId VARCHAR(64) NOT NULL,
  Type VARCHAR(200) NOT NULL,
  ReadOn TIMESTAMP,
  ActionLinkText VARCHAR(2000),
  ActionLinkURL VARCHAR(4000),
  Content TEXT,
  ContentType VARCHAR(100),

  CONSTRAINT PK_Notifications PRIMARY KEY (RowId),
  CONSTRAINT FK_Notifications_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
  CONSTRAINT UQ_Notifications_ContainerUserObjectType UNIQUE (Container, UserId, ObjectId, Type)
);

CREATE INDEX IX_Notification_User ON core.Notifications(UserId);

CREATE TABLE core.DataStates
(
  RowId SERIAL,
  Label VARCHAR(64) NULL,
  Description VARCHAR(500) NULL,
  Container ENTITYID NOT NULL,
  PublicData BOOLEAN NOT NULL,
  CONSTRAINT PK_QCState PRIMARY KEY (RowId),
  CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
);

ALTER TABLE core.DataStates ADD COLUMN StateType VARCHAR(20);

CREATE TABLE core.APIKeys
(
    RowId SERIAL,
    CreatedBy USERID,
    Created TIMESTAMP,
    Crypt VARCHAR(100),
    Expiration TIMESTAMP NULL,

    CONSTRAINT PK_APIKeys PRIMARY KEY (RowId),
    CONSTRAINT UQ_CRYPT UNIQUE (Crypt)
);

CREATE TABLE core.ReportEngines
(
  RowId SERIAL,
  Name VARCHAR(255) NOT NULL,
  CreatedBy USERID,
  ModifiedBy USERID,
  Created TIMESTAMP,
  Modified TIMESTAMP,

  Enabled BOOLEAN NOT NULL DEFAULT FALSE,
  Type VARCHAR(64) NOT NULL,
  Description VARCHAR(255),
  Configuration TEXT,

  CONSTRAINT PK_ReportEngines PRIMARY KEY (RowId),
  CONSTRAINT UQ_Name_Type UNIQUE (Name, Type)
);

CREATE TABLE core.ReportEngineMap
(
  EngineId INTEGER NOT NULL,
  Container ENTITYID NOT NULL,
  EngineContext VARCHAR(64) NOT NULL DEFAULT 'report',

  CONSTRAINT PK_ReportEngineMap PRIMARY KEY (EngineId, Container, EngineContext),
  CONSTRAINT FK_ReportEngineMap_ReportEngines FOREIGN KEY (EngineId) REFERENCES core.ReportEngines (RowId)
);

CREATE TABLE core.PrincipalRelations
(
  userid USERID NOT NULL,
  otherid USERID NOT NULL,
  relationship VARCHAR(100) NOT NULL,
  created TIMESTAMP,

  CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);

CREATE TABLE core.AuthenticationConfigurations
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    Provider VARCHAR(64) NOT NULL,
    Description VARCHAR(255) NOT NULL,
    Enabled BOOLEAN NOT NULL,
    AutoRedirect BOOLEAN NOT NULL DEFAULT FALSE,
    SortOrder SMALLINT NOT NULL DEFAULT 32767, -- ensure that new configurations appear at the bottom of the list by default
    Properties TEXT,
    EncryptedProperties TEXT,

    CONSTRAINT PK_AuthenticationConfigurations PRIMARY KEY (RowId)
);

CREATE TABLE core.EmailOptions
(
    EmailOptionId INT4 NOT NULL,
    EmailOption VARCHAR(50),
    Type VARCHAR(60) NOT NULL DEFAULT 'messages',

    CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
);

INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (0, 'No Email');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (1, 'All conversations');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (2, 'My conversations');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations');

-- new file email notification options
INSERT INTO core.emailOptions (EmailOptionId, EmailOption, Type) VALUES
                                                                     (512, 'No Email', 'files'),
                                                                     (513, '15 minute digest', 'files'),
                                                                     (514, 'Daily digest', 'files');

-- sample manager email notification options
INSERT INTO core.emailOptions (EmailOptionId, EmailOption, Type) VALUES (701, 'No Email', 'samplemanager');
INSERT INTO core.emailOptions (EmailOptionId, EmailOption, Type) VALUES (702, 'All emails', 'samplemanager');

-- labbook email notification options
INSERT INTO core.emailOptions (EmailOptionId, EmailOption, Type) VALUES (801, 'No Email', 'labbook');
INSERT INTO core.emailOptions (EmailOptionId, EmailOption, Type) VALUES (802, 'All emails', 'labbook');

CREATE TABLE core.EmailPrefs
(
    Container ENTITYID,
    UserId USERID,
    EmailOptionId INT4 NOT NULL,
    LastModifiedBy USERID,
    Type VARCHAR(60) NOT NULL DEFAULT 'messages',
    SrcIdentifier VARCHAR(100) NOT NULL, -- allow subscriptions to multiple forums within a single container

    CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId, Type, SrcIdentifier),
    CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
    CONSTRAINT FK_EmailPrefs_EmailOptions FOREIGN KEY (EmailOptionId) REFERENCES core.EmailOptions (EmailOptionId)
);

-- This empty stored procedure doesn't directly change the database, but calling it from a sql script signals the
-- script runner to invoke the specified method at this point in the script running process. See implementations
-- of the UpgradeCode interface for more details.
CREATE FUNCTION core.executeJavaUpgradeCode(text) RETURNS void AS $$
DECLARE note TEXT := 'Empty function that signals script runner to execute Java upgrade code. See implementations of UpgradeCode.java.';
BEGIN
END
$$ LANGUAGE plpgsql;

-- This empty stored procedure is a synonym for core.executeJavaUpgradeCode(), but is meant to denote Java code that is used to
-- initialize data in a schema (e.g., pre-populating a table with values), not transform existing data. We mark these cases with
-- a different procedure name because our bootstrap scripts still need to invoke them, as opposed to invocations of upgrade code
-- which we remove from bootstrap scripts. See implementations of the UpgradeCode interface to find the initialization code.
CREATE FUNCTION core.executeJavaInitializationCode(text) RETURNS void AS $$
DECLARE note TEXT := 'Empty function that signals script runner to execute Java initialization code. See implementations of UpgradeCode.java.';
BEGIN
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION core.sort(anyarray) RETURNS anyarray AS $$
SELECT ARRAY(SELECT $1[i] FROM generate_series(array_lower($1,1), array_upper($1,1)) g(i) ORDER BY 1)
$$ LANGUAGE SQL STRICT IMMUTABLE;

CREATE FUNCTION core.fnCalculateAge (startDate timestamp, endDate timestamp) RETURNS INTEGER AS $$
DECLARE
  /*
    Simple function to calculate the age (number of years, rounded down) between two dates
    Returns NULL if either startDate or endDate is NULL

    No check is made that endDate is after startDate; the calculation is invalid in this case.
   */
  age INTEGER;
BEGIN
  IF startDate IS NULL OR endDate IS NULL THEN
    age := NULL;
  ELSE
    age := EXTRACT(year from AGE(endDate, startDate));
  END IF;
  RETURN age;
END;
$$ LANGUAGE plpgsql;

-- An empty stored procedure (similar to executeJavaUpgradeCode) that, when detected by the script runner,
-- imports a tabular data file (TSV, XLSX, etc.) into the specified table.
CREATE FUNCTION core.bulkImport(text, text, text, boolean = false) RETURNS void AS $$
DECLARE note TEXT := 'Empty function that signals script runner to bulk import a file into a table.';
BEGIN
END
$$ LANGUAGE plpgsql;

-- Add ability to drop columns
CREATE FUNCTION core.fn_dropifexists(
    text,
    text,
    text,
    text)
  RETURNS integer AS
$BODY$
/*
  Function to safely drop most database object types without error if the object does not exist.

  Schema and column deletion will cascade to any dependent objects.

  As Postgres supports function overloads, to drop a function or an aggregate, you must include the argument list as the subobjname (4th parameter).
    For example, to drop this function itself:
    SELECT core.fn_dropifexists('fn_dropifexists', 'core', 'FUNCTION', 'text, text, text, text');
    (but don't drop this function itself unless you really mean it)

     Usage:
     SELECT core.fn_dropifexists(objname, objschema, objtype, subobjname)
     where:
     objname    Required. For TABLE, VIEW, PROCEDURE, FUNCTION, AGGREGATE, this is the name of the object to be dropped
                 for SCHEMA, specify '*' to drop all dependent objects, or NULL to drop an empty schema
                 for INDEX, CONSTRAINT, DEFAULT, or COLUMN, specify the name of the table
     objschema  Required. The name of the schema for the object, or the schema being dropped
     objtype    Required. The type of object being dropped. Valid values are TABLE, VIEW, INDEX, CONSTRAINT, DEFAULT, SCHEMA, PROCEDURE, FUNCTION, AGGREGATE, COLUMN
     subobjtype Required. When dropping INDEX, CONSTRAINT, DEFAULT, or COLUMN, the name of the object being dropped. Otherwise NULL
 */
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
  ELSEIF (UPPER(objtype)) = 'FUNCTION' OR (UPPER(objtype)) = 'PROCEDURE' THEN
    BEGIN
      IF EXISTS( SELECT * FROM information_schema.routines WHERE routine_name = LOWER(objname) AND routine_schema = LOWER(objschema) )
      THEN
        EXECUTE 'DROP FUNCTION ' || fullname || '(' || subobjname || ')';
        ret_code = 1;
      END IF;
    END;
  ELSEIF (UPPER(objtype)) = 'AGGREGATE' THEN
    BEGIN
      IF EXISTS( SELECT * FROM pg_aggregate WHERE aggfnoid = fullname)
      THEN
        EXECUTE 'DROP AGGREGATE ' || fullname || '(' || subobjname || ')';
        ret_code = 1;
      END IF;
    END;
  ELSEIF (UPPER(objtype)) = 'COLUMN' THEN
    BEGIN
      IF EXISTS( SELECT * FROM information_schema.columns WHERE table_name = LOWER(objname) AND table_schema = LOWER(objschema) AND column_name = LOWER(subobjname))
      THEN
        EXECUTE 'ALTER TABLE ' || fullname || ' DROP COLUMN ' || subobjname || ' CASCADE';
        ret_code = 1;
      END IF;
    END;
  ELSE
    RAISE EXCEPTION 'Invalid object type - %;  Valid values are TABLE, VIEW, INDEX, CONSTRAINT, SCHEMA, PROCEDURE, FUNCTION, AGGREGATE, COLUMN ', objtype;
  END IF;

  RETURN ret_code;
END;
$BODY$
LANGUAGE plpgsql VOLATILE
COST 100;

SELECT core.executeJavaInitializationCode('setDefaultExcludedProjects');
