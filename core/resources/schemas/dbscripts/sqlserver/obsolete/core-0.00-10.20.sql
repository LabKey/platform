/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

    CONSTRAINT PK_UsersData PRIMARY KEY (UserId),
    CONSTRAINT UQ_DisplayName UNIQUE (DisplayName)
);

CREATE TABLE core.Containers
(
    _ts TIMESTAMP,
    RowId INT IDENTITY(1, 1),
    EntityId ENTITYID DEFAULT NEWID(),
    CreatedBy USERID,
    Created DATETIME,

    Parent ENTITYID,
    Name VARCHAR(255),
    SortOrder INTEGER NOT NULL DEFAULT 0,
    CaBIGPublished BIT NOT NULL DEFAULT 0,
    Description NVARCHAR(4000),
    Workbook BIT NOT NULL DEFAULT 0,
    Title NVARCHAR(1000),

    CONSTRAINT UQ_Containers_RowId UNIQUE CLUSTERED (RowId),
    CONSTRAINT UQ_Containers_EntityId UNIQUE (EntityId),
    CONSTRAINT UQ_Containers_Parent_Name UNIQUE (Parent, Name),
    CONSTRAINT FK_Containers_Containers FOREIGN KEY (Parent) REFERENCES core.Containers(EntityId)
);

CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId);

-- table for all modules
CREATE TABLE core.Modules
(
    Name NVARCHAR(255),
    ClassName NVARCHAR(255),
    InstalledVersion FLOAT,
    Enabled BIT DEFAULT 1,

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

-- Create a log of events (created, verified, password reset, etc.) associated with users
CREATE TABLE core.UserHistory
(
    UserId USERID,
    Date DATETIME,
    Message VARCHAR(500),

    CONSTRAINT PK_UserHistory PRIMARY KEY (UserId, Date),
    CONSTRAINT FK_UserHistory_UserId FOREIGN KEY (UserId) REFERENCES core.Principals(UserId)
);

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
    Relative BIT NOT NULL,
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

-- Should this container's content be searched during multi-container searches?
ALTER TABLE core.Containers
    ADD Searchable BIT NOT NULL DEFAULT 1;

-- This empty stored procedure doesn't directly change the database, but calling it from a sql script signals the
-- script runner to invoke the specified method at this point in the script running process.  See usages of the
-- UpgradeCode interface for more details.
GO

CREATE PROCEDURE core.executeJavaUpgradeCode(@Name VARCHAR(255)) AS
BEGIN
    DECLARE @notice VARCHAR(255)
    SET @notice = 'Empty function that signals script runner to execute Java code.  See usages of UpgradeCode.java.'
END;

-- Procedure to safely drop tables, views, indexes, constraints, schemas, and unnamed default constraints
GO

CREATE PROCEDURE core.fn_dropifexists (@objname VARCHAR(250), @objschema VARCHAR(50), @objtype VARCHAR(50), @subobjname VARCHAR(250) = NULL)
AS
DECLARE @ret_code INTEGER
DECLARE @fullname VARCHAR(300)
SELECT @ret_code = 0
SELECT @fullname = (LOWER(@objschema) + '.' + LOWER(@objname))
IF (UPPER(@objtype)) = 'TABLE'
BEGIN
    IF OBJECTPROPERTY(OBJECT_ID(@fullname), 'IsTable') =1
    BEGIN
        EXEC('DROP TABLE ' + @fullname )
        SELECT @ret_code = 1
    END
        ELSE IF @objname LIKE '##%' AND OBJECT_ID('tempdb.dbo.' + @objname) IS NOT NULL
    BEGIN
        EXEC('DROP TABLE ' + @objname )
        SELECT @ret_code = 1
    END
END
ELSE IF (UPPER(@objtype)) = 'VIEW'
BEGIN
    IF OBJECTPROPERTY(OBJECT_ID(@fullname),'IsView') =1
    BEGIN
        EXEC('DROP VIEW ' + @fullname )
        SELECT @ret_code =1
    END
END
ELSE IF (UPPER(@objtype)) = 'INDEX'
BEGIN
    DECLARE @fullername VARCHAR(500)
    SELECT @fullername = @fullname + '.' + @subobjname
    IF INDEXPROPERTY(OBJECT_ID(@fullname), @subobjname, 'IndexID') IS NOT NULL
    BEGIN
        EXEC('DROP INDEX ' + @fullername )
        SELECT @ret_code =1
    END
    ELSE IF EXISTS (SELECT * FROM sysindexes si INNER JOIN sysobjects so
            ON si.id = so.id
            WHERE si.name = @subobjname
            AND so.name <> @objname)
        RAISERROR ('Index does not belong to specified table ' , 16, 1)
END
ELSE IF (UPPER(@objtype)) = 'CONSTRAINT'
BEGIN
    IF OBJECTPROPERTY(OBJECT_ID(LOWER(@objschema) + '.' + LOWER(@subobjname)), 'IsConstraint') = 1
    BEGIN
        EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @subobjname)
        SELECT @ret_code =1
    END
END
ELSE IF (UPPER(@objtype)) = 'DEFAULT'
BEGIN
    DECLARE @DEFAULT sysname
    SELECT @DEFAULT = s.name
        FROM sysobjects s
        join syscolumns c ON s.parent_obj = c.id
        WHERE s.xtype = 'd'
        and c.cdefault = s.id
        and parent_obj = OBJECT_ID(@fullname)
        and c.name = @subobjname

    IF @DEFAULT IS NOT NULL AND OBJECTPROPERTY(OBJECT_ID(LOWER(@objschema) + '.' + LOWER(@DEFAULT)), 'IsConstraint') = 1
    BEGIN
        EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @DEFAULT)
        SELECT @ret_code =1
    END
END
ELSE IF (UPPER(@objtype)) = 'SCHEMA'
BEGIN
    DECLARE @uid INT
    SELECT @uid=uid FROM sysusers WHERE name = LOWER(@objschema) AND IsAppRole=1
    IF @uid IS NOT NULL
    BEGIN
        IF (@objname = '*' )
        BEGIN
            DECLARE @soName sysname, @parent INT, @xt CHAR(2), @fkschema sysname
            DECLARE soCursor CURSOR for SELECT so.name, so.xtype, so.parent_obj, su.name
                        FROM sysobjects so
                        INNER JOIN sysusers su ON (so.uid = su.uid)
                        WHERE (so.uid=@uid)
                            OR so.id IN (
                                SELECT fso.id FROM sysforeignkeys sfk
                                INNER JOIN sysobjects fso ON (sfk.constid = fso.id)
                                INNER JOIN sysobjects fsr ON (sfk.rkeyid = fsr.id)
                                WHERE fsr.uid=@uid)

                        ORDER BY (CASE  WHEN xtype='V' THEN 1
                                WHEN xtype='P' THEN 2
                                WHEN xtype='F' THEN 3
                                WHEN xtype='U' THEN 4
                              END)

            OPEN soCursor
            FETCH NEXT FROM soCursor INTO @soName, @xt, @parent, @fkschema
            WHILE @@fetch_status = 0
            BEGIN
                SELECT @fullname = @objschema + '.' + @soName
                IF (@xt = 'V')
                    EXEC('DROP VIEW ' + @fullname)
                ELSE IF (@xt = 'P')
                    EXEC('DROP PROCEDURE ' + @fullname)
                ELSE IF (@xt = 'F')
                BEGIN
                    SELECT @fullname = @fkschema + '.' + OBJECT_NAME(@parent)
                    EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @soName)
                END
                ELSE IF (@xt = 'U')
                    EXEC('DROP TABLE ' + @fullname)

                FETCH NEXT FROM soCursor INTO @soName, @xt, @parent, @fkschema
            END
            CLOSE soCursor
            DEALLOCATE soCursor

            EXEC sp_dropapprole @objschema

            SELECT @ret_code =1
        END
        ELSE IF (@objname = '' OR @objname IS NULL)
        BEGIN
            EXEC sp_dropapprole @objschema
            SELECT @ret_code =1
        END
        ELSE
            RAISERROR ('Invalid @objname for @objtype of SCHEMA   must be either "*" (to drop all dependent objects) or NULL (for dropping empty schema )' , 16, 1)
    END
END
ELSE
    RAISERROR('Invalid object type - %s   Valid values are TABLE, VIEW, INDEX, CONSTRAINT, DEFAULT, SCHEMA ', 16,1, @objtype )

RETURN @ret_code;

GO

CREATE SCHEMA portal;
GO

CREATE TABLE portal.PortalWebParts
(
    PageId ENTITYID NOT NULL,
    [Index] INT NOT NULL,
    Name VARCHAR(64),
    Location VARCHAR(16),    -- 'body', 'left', 'right'
    Properties TEXT,    -- url encoded properties
    Permanent BIT NOT NULL DEFAULT 0,

    CONSTRAINT PK_PortalWebParts PRIMARY KEY (PageId, [Index])
);

/* portal-10.10-10.20.sql */

ALTER TABLE Portal.PortalWebParts
    ADD RowId INT IDENTITY(1, 1) NOT NULL;

ALTER TABLE Portal.PortalWebParts
    DROP CONSTRAINT PK_PortalWebParts;

ALTER TABLE Portal.PortalWebParts
    ADD CONSTRAINT PK_PortalWebParts PRIMARY KEY (RowId);
