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

CREATE SCHEMA core;
GO
CREATE SCHEMA temp;
GO

EXEC sp_addtype 'ENTITYID', 'UNIQUEIDENTIFIER';
EXEC sp_addtype 'USERID', 'INT';
GO

-- for JDBC Login support, validates email/password,
-- UserId is stored in the Principals table
-- LDAP authenticated users are not in this table

CREATE TABLE core.Logins
(
    Email VARCHAR(255) NOT NULL,
    Crypt VARCHAR(64) NOT NULL,
    Verification VARCHAR(64),
    LastChanged DATETIME NULL,
    PreviousCrypts VARCHAR(1000),
    RequestedEmail NVARCHAR(255),
    VerificationTimeout DATETIME,

    CONSTRAINT PK_Logins PRIMARY KEY (Email)
);

-- Principals is used for managing security related information
-- It is not used for validating login, that requires an 'external'
-- process, either using SMB, LDAP, JDBC etc (see Logins table)
--
-- It does not contain contact info and other generic user visible data

CREATE TABLE core.Principals
(
    UserId USERID IDENTITY(1000,1),   -- user or group
    Container ENTITYID,               -- NULL for all users, NOT NULL for _ALL_ groups
    OwnerId ENTITYID NULL,
    Name NVARCHAR(64),                -- email (must contain @ and .), group name (no punctuation), or hidden (no @)
    Type CHAR(1),                     -- 'u'=user 'g'=group (NYI 'r'=role, 'm'=managed(module specific)
    Active BIT NOT NULL DEFAULT 1,

    CONSTRAINT PK_Principals PRIMARY KEY (UserId),
    CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId)
);

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
    _ts TIMESTAMP,
    EntityId ENTITYID DEFAULT NEWID(),
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Owner USERID NULL,

    UserId USERID,

    DisplayName NVARCHAR(64) NOT NULL,
    FirstName NVARCHAR(64) NULL,
    LastName NVARCHAR(64) NULL,
    Phone NVARCHAR(64) NULL,
    Mobile NVARCHAR(64) NULL,
    Pager NVARCHAR(64) NULL,
    IM NVARCHAR(64) NULL,
    Description NVARCHAR(255),
    LastLogin DATETIME,
    ExpirationDate DATETIME,

    CONSTRAINT PK_UsersData PRIMARY KEY (UserId),
    CONSTRAINT UQ_DisplayName UNIQUE (DisplayName)
);

ALTER TABLE core.UsersData ADD System BIT NOT NULL DEFAULT 0;

CREATE TABLE core.Containers
(
    _ts TIMESTAMP,
    RowId INT IDENTITY(1, 1),
    EntityId ENTITYID DEFAULT NEWID(),
    CreatedBy USERID,
    Created DATETIME,

    Parent ENTITYID,
    Name NVARCHAR(255),
    SortOrder INTEGER NOT NULL DEFAULT 0,
    Searchable BIT NOT NULL DEFAULT 1,     -- Should this container's content be searched during multi-container searches?

    Description NVARCHAR(4000),
    Title NVARCHAR(1000),
    Type VARCHAR(16) CONSTRAINT DF_Container_Type DEFAULT 'normal' NOT NULL,

    CONSTRAINT UQ_Containers_RowId UNIQUE CLUSTERED (RowId),
    CONSTRAINT UQ_Containers_EntityId UNIQUE (EntityId),
    CONSTRAINT UQ_Containers_Parent_Name UNIQUE (Parent, Name),
    CONSTRAINT FK_Containers_Containers FOREIGN KEY (Parent) REFERENCES core.Containers(EntityId)
);

CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId);

ALTER TABLE core.Containers ADD LockState VARCHAR(25) NULL;
ALTER TABLE core.Containers ADD ExpirationDate DATETIME NULL;

-- table for all modules
CREATE TABLE core.Modules
(
    Name NVARCHAR(255),
    ClassName NVARCHAR(255),
    SchemaVersion FLOAT NULL,
    Enabled BIT DEFAULT 1,
    AutoUninstall BIT NOT NULL DEFAULT 0,   -- TRUE means LabKey should uninstall this module (drop schemas, delete SqlScripts rows, delete Modules rows), if it no longer exists
    Schemas NVARCHAR(4000) NULL,            -- Schemas managed by this module; LabKey will drop these schemas when a module marked AutoUninstall = TRUE is missing

    CONSTRAINT PK_Modules PRIMARY KEY (Name)
);

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
);

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
    DocumentName NVARCHAR(195),        --filename

    DocumentSize INT DEFAULT -1,
    DocumentType VARCHAR(500) DEFAULT 'text/plain',  -- Needs to be large enough to handle new Office document mime-types
    Document IMAGE,            -- ContentType LIKE application/*

    LastIndexed DATETIME NULL,

    CONSTRAINT PK_Documents PRIMARY KEY (RowId),
    CONSTRAINT UQ_Documents_Parent_DocumentName UNIQUE (Parent, DocumentName)
);

CREATE INDEX IX_Documents_Container ON core.Documents(Container);
CREATE INDEX IX_Documents_Parent ON core.Documents(Parent);

CREATE TABLE core.Report
(
    RowId INT IDENTITY(1,1) NOT NULL,
    ReportKey NVARCHAR(255),
    CreatedBy USERID,
    ModifiedBy USERID,
    Created DATETIME,
    Modified DATETIME,
    ContainerId ENTITYID NOT NULL,
    EntityId ENTITYID NULL,
    DescriptorXML TEXT,
    ReportOwner INT,
    Flags INT NOT NULL DEFAULT 0,
    CategoryId INT,
    DisplayOrder INT NOT NULL DEFAULT 0,
    ContentModified DATETIME NOT NULL,

    CONSTRAINT PK_Report PRIMARY KEY (RowId),
    CONSTRAINT FK_Report_ContainerId FOREIGN KEY (ContainerId) REFERENCES core.Containers (EntityId)
);

CREATE INDEX IDX_Report_ContainerId ON core.Report(ContainerId);

CREATE TABLE core.ContainerAliases
(
    Path NVARCHAR(255) NOT NULL,
    ContainerId ENTITYID NOT NULL,

    CONSTRAINT UK_ContainerAliases_Paths UNIQUE (Path),
    CONSTRAINT FK_ContainerAliases_Containers FOREIGN KEY (ContainerId) REFERENCES core.Containers(EntityId)
);

ALTER TABLE core.containeraliases ALTER COLUMN path NVARCHAR(4000);
CREATE TABLE core.MappedDirectories
(
    EntityId ENTITYID NOT NULL,
    Container ENTITYID NOT NULL,
    Relative BIT NOT NULL,
    Name NVARCHAR(80),
    Path NVARCHAR(255),

    CONSTRAINT PK_MappedDirecctories PRIMARY KEY (EntityId),
    CONSTRAINT UQ_MappedDirectories UNIQUE (Container,Name)
);

CREATE TABLE core.Policies
(
    ResourceId ENTITYID NOT NULL,
    ResourceClass VARCHAR(1000),
    Container ENTITYID NOT NULL,
    Modified DATETIME NOT NULL,

    CONSTRAINT PK_Policies PRIMARY KEY(ResourceId)
);

CREATE TABLE core.RoleAssignments
(
    ResourceId ENTITYID NOT NULL,
    UserId USERID NOT NULL,
    Role VARCHAR(500) NOT NULL,

    CONSTRAINT PK_RoleAssignments PRIMARY KEY(ResourceId, UserId, Role),
    CONSTRAINT FK_RA_P FOREIGN KEY(ResourceId) REFERENCES core.Policies(ResourceId),
    CONSTRAINT FK_RA_UP FOREIGN KEY(UserId) REFERENCES core.Principals(UserId)
);

CREATE TABLE core.MvIndicators
(
    Container ENTITYID NOT NULL,
    MvIndicator VARCHAR(64) NOT NULL,
    Label VARCHAR(255),

    CONSTRAINT PK_MvIndicators_Container_MvIndicator PRIMARY KEY (Container, MvIndicator)
);

-- CONSIDER: eventually switch to entityid PK/FK
CREATE TABLE core.PortalPages
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    Container ENTITYID NOT NULL,
    PageId VARCHAR(50) NOT NULL,
    "index" INTEGER NOT NULL,
    Caption VARCHAR(64),
    Hidden BIT NOT NULL DEFAULT 0,
    Type VARCHAR(20), -- 'portal', 'folder', 'action'
    -- associate page with a registered folder type
    -- folderType VARCHAR(64),
    Action VARCHAR(200),    -- type='action' see DetailsURL
    TargetFolder ENTITYID,  -- type=='folder'
    Permanent BIT NOT NULL DEFAULT 0, -- may not be renamed,hidden,deleted (w/o changing folder type)
    Properties TEXT,

    CONSTRAINT PK_PortalPages PRIMARY KEY (RowId),
    CONSTRAINT FK_PortalPages_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);

CREATE INDEX IX_PortalPages_EntityId ON core.PortalPages(EntityId);

CREATE TABLE core.PortalWebParts
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Container ENTITYID NOT NULL,
    [Index] INT NOT NULL,
    Name VARCHAR(64),
    Location VARCHAR(16),    -- 'body', 'left', 'right'
    Properties TEXT,    -- url encoded properties
    Permanent BIT NOT NULL DEFAULT 0,
    Permission VARCHAR(256) NULL,
    PermissionContainer ENTITYID NULL,
    PortalPageId INT NOT NULL,

    CONSTRAINT PK_PortalWebParts PRIMARY KEY (RowId),
    CONSTRAINT FK_PortalWebParts_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_PortalWebParts_PermissionContainer FOREIGN KEY (PermissionContainer) REFERENCES core.Containers (EntityId) ON UPDATE NO ACTION ON DELETE SET NULL,
    CONSTRAINT FK_PortalWebPartPages FOREIGN KEY (PortalPageId) REFERENCES core.PortalPages (rowId)
);

-- Add an index and FK on the Container column
CREATE INDEX IX_PortalWebParts ON core.PortalWebParts(Container);

-- represents a grouping category for reports and datasets
CREATE TABLE core.ViewCategory
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME DEFAULT getdate(),
    ModifiedBy USERID,
    Modified DATETIME DEFAULT getdate(),

    Label NVARCHAR(200) NOT NULL,
    DisplayOrder INT NOT NULL DEFAULT 0,

    Parent INT,

    CONSTRAINT pk_viewCategory PRIMARY KEY (RowId),
    CONSTRAINT uq_container_label_parent UNIQUE (Container, Label, Parent),
    CONSTRAINT FK_ViewCategory_Parent FOREIGN KEY (Parent) REFERENCES core.ViewCategory(RowId)
);

CREATE TABLE core.DbSequences
(
    RowId INT IDENTITY,
    Container ENTITYID NOT NULL,
    Name VARCHAR(500) NOT NULL,
    Id INTEGER NOT NULL,
    Value BIGINT NOT NULL,

    CONSTRAINT PK_DbSequences PRIMARY KEY (RowId),
    CONSTRAINT UQ_DbSequences_Container_Name_Id UNIQUE (Container, Name, Id)
);

CREATE TABLE core.ShortURL
(
    RowId INT IDENTITY(1, 1),
    EntityId ENTITYID NOT NULL,
    ShortURL NVARCHAR(255) NOT NULL,
    FullURL NVARCHAR(4000) NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_ShortURL PRIMARY KEY (RowId),
    CONSTRAINT UQ_ShortURL_EntityId UNIQUE (EntityId),
    CONSTRAINT UQ_ShortURL_ShortURL UNIQUE (ShortURL)
);

CREATE TABLE core.Notifications
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,

    UserId USERID NOT NULL,
    ObjectId NVARCHAR(64) NOT NULL,
    Type NVARCHAR(200) NOT NULL,
    ReadOn DATETIME,
    ActionLinkText NVARCHAR(2000),
    ActionLinkURL NVARCHAR(4000),
    Content NVARCHAR(MAX),
    ContentType NVARCHAR(100),

    CONSTRAINT PK_Notifications PRIMARY KEY (RowId),
    CONSTRAINT FK_Notifications_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT UQ_Notifications_ContainerUserObjectType UNIQUE (Container, UserId, ObjectId, Type)
);

CREATE INDEX IX_Notification_User ON core.Notifications(UserId);

CREATE TABLE core.DataStates
(
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(64) NULL,
    Description NVARCHAR(500) NULL,
    Container ENTITYID NOT NULL,
    PublicData BIT NOT NULL,

    CONSTRAINT PK_QCState PRIMARY KEY (RowId),
    CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
);

ALTER TABLE core.DataStates ADD StateType NVARCHAR(20);

CREATE TABLE core.APIKeys
(
    RowId INT IDENTITY(1, 1),
    CreatedBy USERID,
    Created DATETIME,
    Crypt VARCHAR(100),
    Expiration DATETIME NULL,

    CONSTRAINT PK_APIKeys PRIMARY KEY (RowId),
    CONSTRAINT UQ_CRYPT UNIQUE (Crypt)
);

CREATE TABLE core.ReportEngines
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Name NVARCHAR(255) NOT NULL,
    CreatedBy USERID,
    ModifiedBy USERID,
    Created DATETIME,
    Modified DATETIME,

    Enabled BIT NOT NULL DEFAULT 0,
    Type NVARCHAR(64) NOT NULL,
    Description NVARCHAR(255),
    Configuration NVARCHAR(MAX),

    CONSTRAINT PK_ReportEngines PRIMARY KEY (RowId),
    CONSTRAINT UQ_Name_Type UNIQUE (Name, Type)
);

CREATE TABLE core.ReportEngineMap
(
    EngineId INTEGER NOT NULL,
    Container ENTITYID NOT NULL,
    EngineContext NVARCHAR(64) NOT NULL DEFAULT 'report',

    CONSTRAINT PK_ReportEngineMap PRIMARY KEY (EngineId, Container, EngineContext),
    CONSTRAINT FK_ReportEngineMap_ReportEngines FOREIGN KEY (EngineId) REFERENCES core.ReportEngines (RowId)
);

CREATE TABLE core.PrincipalRelations
(
    userid USERID NOT NULL,
    otherid USERID NOT NULL,
    relationship NVARCHAR(100) NOT NULL,
    created DATETIME,

    CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);

CREATE TABLE core.AuthenticationConfigurations
(
    RowId INT IDENTITY(1,1),
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    Provider NVARCHAR(64) NOT NULL,
    Description NVARCHAR(255) NOT NULL,
    Enabled BIT NOT NULL,
    AutoRedirect BIT NOT NULL DEFAULT 0,
    SortOrder SMALLINT NOT NULL DEFAULT 32767, -- ensure that new configurations appear at the bottom of the list by default
    Properties NVARCHAR(MAX),
    EncryptedProperties NVARCHAR(MAX),

    CONSTRAINT PK_AuthenticationConfigurations PRIMARY KEY (RowId)
);

CREATE TABLE core.EmailOptions
(
    EmailOptionId INT NOT NULL,
    EmailOption NVARCHAR(50),
    Type NVARCHAR(60) NOT NULL DEFAULT 'messages',

    CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
);

INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (0, 'No Email');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (1, 'All conversations');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (2, 'My conversations');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations');

-- new file email notification options
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption, Type) VALUES (512, 'No Email', 'files');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption, Type) VALUES (513, '15 minute digest', 'files');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption, Type) VALUES (514, 'Daily digest', 'files');

-- sample manager email notification options
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption, Type) VALUES (701, 'No Email', 'samplemanager');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption, Type) VALUES (702, 'All emails', 'samplemanager');

-- labbook email notification options
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption, Type) VALUES (801, 'No Email', 'labbook');
INSERT INTO core.EmailOptions (EmailOptionId, EmailOption, Type) VALUES (802, 'All emails', 'labbook');

CREATE TABLE core.EmailPrefs
(
    Container ENTITYID,
    UserId USERID,
    EmailOptionId INT NOT NULL,
    LastModifiedBy USERID,
    Type NVARCHAR(60) NOT NULL DEFAULT 'messages',
    SrcIdentifier NVARCHAR(100) NOT NULL,  -- allow subscriptions to multiple forums within a single container

    CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId, Type, SrcIdentifier),
    CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
    CONSTRAINT FK_EmailPrefs_EmailOptions FOREIGN KEY (EmailOptionId) REFERENCES core.EmailOptions (EmailOptionId)
);

GO

-- This empty stored procedure doesn't directly change the database, but calling it from a sql script signals the
-- script runner to invoke the specified method at this point in the script running process. See implementations of
-- the UpgradeCode interface for more details.
CREATE PROCEDURE core.executeJavaUpgradeCode(@Name VARCHAR(255)) AS
BEGIN
    DECLARE @notice VARCHAR(255)
    SET @notice = 'Empty function that signals script runner to execute Java initialization code. See implementations of UpgradeCode.java.'
END;

GO

-- This empty stored procedure is a synonym for core.executeJavaUpgradeCode(), but is meant to denote Java code that is used to
-- initialize data in a schema (e.g., pre-populating a table with values), not transform existing data. We mark these cases with
-- a different procedure name because our bootstrap scripts still need to invoke them, as opposed to invocations of upgrade code
-- which we remove from bootstrap scripts. See implementations of the UpgradeCode interface to find the initialization code.
CREATE PROCEDURE core.executeJavaInitializationCode(@Name VARCHAR(255)) AS
BEGIN
DECLARE @notice VARCHAR(255)
SET @notice = 'Empty function that signals script runner to execute initialization Java code. See implementations of UpgradeCode.java.'
END;

GO

CREATE FUNCTION core.fnCalculateAge(@startDate DATETIME, @endDate DATETIME) RETURNS INT
AS
  BEGIN
/*
  Simple function to calculate the age (number of years, rounded down) between two dates
  Returns NULL if either startDate or endDate is NULL

  No check is made that endDate is after startDate; the calculation is invalid in this case.
*/
    DECLARE @age INT;

    IF @startDate IS NULL OR @endDate IS NULL SET @age = NULL
    ELSE
      BEGIN
        SET @age = YEAR(@endDate) - YEAR(@startDate) -
                   CASE WHEN MONTH(@endDate) < MONTH(@startDate) OR
                             (MONTH(@endDate) = MONTH(@startDate) AND DAY(@endDate) < DAY(@startDate))
                   THEN 1
                   ELSE 0
                   END
      END

    RETURN @age
  END;

GO

-- An empty stored procedure (similar to executeJavaUpgradeCode) that, when detected by the script runner,
-- imports a tabular data file (TSV, XLSX, etc.) into the specified table.
CREATE PROCEDURE core.bulkImport(@schema VARCHAR(200), @table VARCHAR(200), @filename VARCHAR(200), @preserveEmptyString bit = 0) AS
  BEGIN
    DECLARE @notice VARCHAR(255)
    SET @notice = 'Empty function that signals script runner to bulk import a file into a table.'
  END;

GO

CREATE PROCEDURE [core].[fn_dropifexists] (@objname VARCHAR(250), @objschema VARCHAR(50), @objtype VARCHAR(50), @subobjname VARCHAR(250) = NULL, @printCmds BIT = 0)
AS
BEGIN
  /*
    Procedure to safely drop most database object types without error if the object does not exist. Schema deletion
     will cascade to the tables and programability objects in that schema. Column deletion will cascade to any keys,
     constraints, and indexes on the column.
       Usage:
       EXEC core.fn_dropifexists objname, objschema, objtype, subobjname, printCmds
       where:
       objname    Required. For TABLE, VIEW, PROCEDURE, FUNCTION, AGGREGATE, SYNONYM, this is the name of the object to be dropped
                   for SCHEMA, specify '*' to drop all dependent objects, or NULL to drop an empty schema
                   for INDEX, CONSTRAINT, DEFAULT, or COLUMN, specify the name of the table
       objschema  Required. The name of the schema for the object, or the schema being dropped
       objtype    Required. The type of object being dropped. Valid values are TABLE, VIEW, INDEX, CONSTRAINT, DEFAULT, SCHEMA, PROCEDURE, FUNCTION, AGGREGATE, SYNONYM, COLUMN
       subobjtype Optional. When dropping INDEX, CONSTRAINT, DEFAULT, or COLUMN, the name of the object being dropped
       printCmds  Optional, 1 or 0. If 1, the cascading drop commands for SCHEMA and COLUMN will be printed for debugging purposes

    Note for implementors: Names that are part of SQL commands executed below should be bracketed to handle names that
    have spaces, special characters, or reserved words. Names that are used in comparisons, in this code and in WHERE
    clauses, must not be bracketed. @fullname is bracketed, since it's only used in SQL commands. All other vars are not
    bracketed since they're often used in comparisons. When referencing @objname, @objschema, @subobjname, etc. be sure
    to add brackets where required.
   */
  DECLARE @ret_code INTEGER
  DECLARE @fullname VARCHAR(500)
  DECLARE @fkConstName sysname, @fkTableName sysname, @fkSchema sysname
  SELECT @ret_code = 0
  SELECT @fullname = ('[' + @objschema + '].[' + @objname + ']') -- @fullname is always bracketed, to handle names that are reserved words, etc.
  IF (UPPER(@objtype)) = 'TABLE'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(@fullname), 'IsTable') =1
        BEGIN
          EXEC('DROP TABLE ' + @fullname )
          SELECT @ret_code = 1
        END
      ELSE IF @objname LIKE '##%' AND OBJECT_ID('tempdb.dbo.[' + @objname + ']') IS NOT NULL
        BEGIN
          EXEC('DROP TABLE [' + @objname + ']')
          SELECT @ret_code = 1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'VIEW'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(@fullname), 'IsView') =1
        BEGIN
          EXEC('DROP VIEW ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'INDEX'
    BEGIN
      DECLARE @fullername VARCHAR(500)
      SELECT @fullername = @fullname + '.[' + @subobjname + ']' -- Unlikely to require brackets, but doesn't hurt
      IF INDEXPROPERTY(OBJECT_ID(@fullname), @subobjname, 'IndexID') IS NOT NULL
        BEGIN
          EXEC('DROP INDEX ' + @fullername )
          SELECT @ret_code =1
        END
      ELSE IF EXISTS (SELECT * FROM sys.indexes si
      WHERE si.name = @subobjname
            AND OBJECT_NAME(si.object_id) <> @objname)
        BEGIN
          RAISERROR ('Index does not belong to specified table ' , 16, 1)
          RETURN @ret_code
        END
    END
  ELSE IF (UPPER(@objtype)) = 'CONSTRAINT'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(@objschema + '.' + @subobjname), 'IsConstraint') = 1
        BEGIN
          EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @subobjname + ']')
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'DEFAULT'
    BEGIN
      DECLARE @DEFAULT sysname
      SELECT 	@DEFAULT = s.name
      FROM sys.objects s
        join sys.columns c ON s.object_id = c.default_object_id
      WHERE
        s.type = 'D'
        and c.object_id = OBJECT_ID(@fullname)
        and c.name = @subobjname

      IF @DEFAULT IS NOT NULL AND OBJECTPROPERTY(OBJECT_ID(@objschema + '.' + @DEFAULT), 'IsConstraint') = 1
        BEGIN
          EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @DEFAULT + ']')
          if (@printCmds = 1) PRINT('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @DEFAULT + ']')
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'SCHEMA'
    BEGIN
      DECLARE @schemaid INT, @principalid int

      SELECT @schemaid=schema_id, @principalid=principal_id
      FROM sys.schemas
      WHERE name = @objschema

      IF @schemaid IS NOT NULL
        BEGIN
          IF (@objname is NOT NULL AND @objname NOT IN ('', '*'))
            BEGIN
              RAISERROR ('Invalid @objname for @objtype of SCHEMA; must be either "*" (to drop all dependent objects) or NULL (for dropping empty schema)' , 16, 1)
              RETURN @ret_code
            END
          ELSE IF (@objname = '*' )
            BEGIN
              DECLARE fkCursor CURSOR LOCAL for
                SELECT object_name(sfk.object_id) as fk_constraint_name, object_name(sfk.parent_object_id) as fk_table_name,
                       schema_name(sfk.schema_id) as fk_schema_name
                FROM sys.foreign_keys sfk
                  INNER JOIN sys.objects fso ON (sfk.referenced_object_id = fso.object_id)
                WHERE fso.schema_id=@schemaid
                      AND sfk.type = 'F'

              OPEN fkCursor
              FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
              WHILE @@fetch_status = 0
                BEGIN
                  SELECT @fullname = '[' + @fkSchema + '].[' +@fkTableName + ']'
                  EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @fkConstName + ']')
                  if (@printCmds = 1) PRINT('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @fkConstName + ']')

                  FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
                END
              CLOSE fkCursor
              DEALLOCATE fkCursor

              DECLARE @soName sysname, @parent INT, @type CHAR(2), @fkschemaid int
              DECLARE soCursor CURSOR LOCAL for
                SELECT so.name, so.type, so.parent_object_id, so.schema_id
                FROM sys.objects so
                WHERE (so.schema_id=@schemaid)
                ORDER BY (CASE  WHEN so.type='V' THEN 1
                          WHEN so.type='P' THEN 2
                          WHEN so.type IN ('FN', 'IF', 'TF', 'FS', 'FT') THEN 3
                          WHEN so.type='AF' THEN 4
                          WHEN so.type='U' THEN 5
                          WHEN so.type='SN' THEN 6
                          ELSE 7
                          END)
              OPEN soCursor
              FETCH NEXT FROM soCursor INTO @soName, @type, @parent, @fkschemaid
              WHILE @@fetch_status = 0
                BEGIN
                  SELECT @fullname = '[' + @objschema + '].[' + @soName + ']'
                  IF (@type = 'V')
                    BEGIN
                      EXEC('DROP VIEW ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP VIEW ' + @fullname)
                    END
                  ELSE IF (@type = 'P')
                    BEGIN
                      EXEC('DROP PROCEDURE ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP PROCEDURE ' + @fullname)
                    END
                  ELSE IF (@type IN ('FN', 'IF', 'TF', 'FS', 'FT'))
                    BEGIN
                      EXEC('DROP FUNCTION ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP FUNCTION ' + @fullname)
                    END
                  ELSE IF (@type = 'AF')
                    BEGIN
                      EXEC('DROP AGGREGATE ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP AGGREGATE ' + @fullname)
                    END
                  ELSE IF (@type = 'U')
                    BEGIN
                      EXEC('DROP TABLE ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP TABLE ' + @fullname)
                    END
                  ELSE IF (@type = 'SN')
                    BEGIN
                      EXEC('DROP SYNONYM ' + @fullname)
                      if (@printCmds = 1) PRINT('DROP SYNONYM ' + @fullname)
                    END
                  ELSE
                    BEGIN
                      DECLARE @msg NVARCHAR(255)
                      SELECT @msg=' Found object of type: ' + @type + ' name: ' + @fullname + ' in this schema. Schema not dropped. '
                      RAISERROR (@msg, 16, 1)
                      RETURN @ret_code
                    END
                  FETCH NEXT FROM soCursor INTO @soName, @type, @parent, @fkschemaid
                END
              CLOSE soCursor
              DEALLOCATE soCursor
            END

          IF (@objSchema != 'dbo')
            BEGIN
              DECLARE @approlename sysname
              SELECT @approlename = name
              FROM sys.database_principals
              WHERE principal_id=@principalid AND type='A'

              IF (@approlename IS NOT NULL)
                BEGIN
                  EXEC sp_dropapprole @approlename
                  if (@printCmds = 1) PRINT ('sp_dropapprole '+ @approlename)
                END
              ELSE
                BEGIN
                  EXEC('DROP SCHEMA [' + @objschema + ']')
                  if (@printCmds = 1) PRINT('DROP SCHEMA [' + @objschema + ']')
                END
            END
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'PROCEDURE'
    BEGIN
      IF (@objschema = 'sys')
        BEGIN
          RAISERROR ('Invalid @objschema, not attempting to drop sys object', 16, 1)
          RETURN @ret_code
        END
      IF OBJECTPROPERTY(OBJECT_ID(@fullname), 'IsProcedure') =1
        BEGIN
          EXEC('DROP PROCEDURE ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'FUNCTION'
    BEGIN
      IF EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON o.schema_id = s.schema_id WHERE s.name = @objschema AND o.name = @objname AND o.type IN ('FN', 'IF', 'TF', 'FS', 'FT'))
        BEGIN
          EXEC('DROP FUNCTION ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'AGGREGATE'
    BEGIN
      IF EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON o.schema_id = s.schema_id WHERE s.name = @objschema AND o.name = @objname AND o.type = 'AF')
        BEGIN
          EXEC('DROP AGGREGATE ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'SYNONYM'
    BEGIN
      IF EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON o.schema_id = s.schema_id WHERE s.name = @objschema AND o.name = @objname AND o.type = 'SN')
        BEGIN
          EXEC('DROP SYNONYM ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'COLUMN'
    BEGIN
      DECLARE @tableID		INT
      SET @tableID = OBJECT_ID(@fullname)
      IF EXISTS (SELECT 1 FROM sys.columns WHERE Name = N'' + @subobjname AND Object_ID = @tableID)
        BEGIN
          -- Drop any indexes and constraints on the column
          DECLARE @index          SYSNAME
          DECLARE cur_indexes CURSOR FOR
            SELECT
              i.Name
            FROM
              sys.indexes i
              INNER JOIN
              sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
              INNER JOIN
              sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE
              c.name = @subobjname
              AND is_primary_key = 0
              AND i.object_id = @tableID

          DECLARE fkCursor CURSOR LOCAL for
            SELECT object_name(fk.object_id), object_name(fkc.parent_object_id), schema_name(fk.schema_id)
            FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
              JOIN sys.columns c ON fkc.referenced_object_id = c.object_id AND c.column_id = fkc.referenced_column_id
            WHERE fk.referenced_object_id = @tableID AND c.name = @subobjname

          BEGIN TRANSACTION
          BEGIN TRY
          -- Drop indexes
          OPEN cur_indexes
          FETCH NEXT FROM cur_indexes INTO @index
          WHILE (@@FETCH_STATUS = 0)
            BEGIN
              if (@printCmds = 1) PRINT('DROP INDEX [' + @index + '] ON ' + @fullname + '-- index drop')
              EXEC('DROP INDEX [' + @index + '] ON ' + @fullname)
              FETCH NEXT FROM cur_indexes INTO @index
            END
          CLOSE cur_indexes

          -- Drop foreign keys
          DECLARE @fkFullName sysname
          OPEN fkCursor
          FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
          WHILE @@fetch_status = 0
            BEGIN
              SELECT @fkFullName = '[' + @fkSchema + '].[' +@fkTableName + ']'
              if (@printCmds = 1) PRINT('ALTER TABLE ' + @fkFullName + ' DROP CONSTRAINT [' + @fkConstName + '] -- FK drop')
              EXEC('ALTER TABLE ' + @fkFullname + ' DROP CONSTRAINT [' + @fkConstName + ']')
              FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
            END
          CLOSE fkCursor

          -- Drop default constraint on column
          DECLARE @ConstraintName nvarchar(200)
          SELECT @ConstraintName = Name FROM SYS.DEFAULT_CONSTRAINTS WHERE PARENT_OBJECT_ID = @tableID AND PARENT_COLUMN_ID = (SELECT column_id FROM sys.columns WHERE NAME = N'' + @subobjname AND object_id = @tableID)
          IF @ConstraintName IS NOT NULL
            BEGIN
              if (@printCmds = 1) PRINT ('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @ConstraintName + '] -- default constraint drop')
              EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @ConstraintName + ']')
            END

          -- Drop other constraints, including PK
          SET @ConstraintName = NULL
          while 0=0 begin
            set @constraintName = (
              select top 1 constraint_name
              from information_schema.constraint_column_usage
              where TABLE_SCHEMA = @objschema and table_name = @objname and column_name = @subobjname )
            if @constraintName is null break
            if (@printCmds = 1) PRINT ('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @ConstraintName + '] -- other constraint drop')
            exec ('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT [' + @ConstraintName + ']')
          end

          -- Now drop the column
          if (@printCmds = 1) PRINT ('ALTER TABLE ' + @fullname + ' DROP COLUMN [' + @subobjname + ']')
          EXEC('ALTER TABLE ' + @fullname + ' DROP COLUMN [' + @subobjname + ']')
          SELECT @ret_code =1

          DEALLOCATE cur_indexes
          DEALLOCATE fkCursor
          COMMIT TRANSACTION
          END TRY
          BEGIN CATCH
          ROLLBACK TRANSACTION
          DEALLOCATE cur_indexes
          DEALLOCATE fkCursor

          DECLARE @error varchar(max)
          SET @error = 'Error dropping column %s. The column has not been changed. This procedure can automatically drop indexes, foreign keys or primary keys, defaults, and other constraints on a column, but not other objects such as triggers or rules.
			Original error from SQL Server was: ' + ERROR_MESSAGE()
          RAISERROR(@error, 16, 1, @subobjname)
          END CATCH
        END
    END
  ELSE
    RAISERROR('Invalid object type - %s   Valid values are TABLE, VIEW, INDEX, CONSTRAINT, DEFAULT, SCHEMA, PROCEDURE, FUNCTION, AGGREGATE, SYNONYM, COLUMN', 16, 1, @objtype )

  RETURN @ret_code;
END;

GO

EXEC core.executeJavaInitializationCode 'setDefaultExcludedProjects';
