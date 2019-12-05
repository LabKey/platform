/*
 * Copyright (c) 2017-2019 LabKey Corporation
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

CREATE SCHEMA comm;
GO

CREATE TABLE comm.Announcements
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,
    Parent ENTITYID,
    Title NVARCHAR(255),
    Expires DATETIME,
    Body NTEXT,
    RendererType NVARCHAR(50) NULL,  -- Updates to properties will result in NULL body and NULL render type
    Status VARCHAR(50) NULL,
    AssignedTo USERID NULL,
    DiscussionSrcIdentifier NVARCHAR(100) NULL,
    DiscussionSrcURL NVARCHAR(1000) NULL,
    LastIndexed DATETIME NULL,

    CONSTRAINT PK_Announcements PRIMARY KEY (RowId),
    CONSTRAINT UQ_Announcements UNIQUE CLUSTERED (Container, Parent, RowId)
);

CREATE INDEX IX_DiscussionSrcIdentifier ON comm.announcements(Container, DiscussionSrcIdentifier);

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
    Parent INT NOT NULL,
    DisplayOrder FLOAT NOT NULL,
    PageVersionId INT NULL,
    ShowAttachments BIT NOT NULL DEFAULT 1,
    LastIndexed DATETIME NULL,
    ShouldIndex BIT DEFAULT 1,

    CONSTRAINT PK_Pages PRIMARY KEY (EntityId),
    CONSTRAINT UQ_Pages UNIQUE CLUSTERED (Container, Name)
);

CREATE TABLE comm.PageVersions
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    PageEntityId ENTITYID NOT NULL,
    CreatedBy USERID NULL,
    Created DATETIME NULL,
    Owner USERID NULL,
    Version INT NOT NULL,
    Title NVARCHAR (255),
    Body NTEXT,
    RendererType NVARCHAR(50) NOT NULL DEFAULT 'RADEOX',

    CONSTRAINT PK_PageVersions PRIMARY KEY (RowId),
    CONSTRAINT FK_PageVersions_Pages FOREIGN KEY (PageEntityId) REFERENCES comm.Pages(EntityId),
    CONSTRAINT UQ_PageVersions UNIQUE (PageEntityId, Version)
);

ALTER TABLE comm.Pages
    ADD CONSTRAINT FK_Pages_PageVersions FOREIGN KEY (PageVersionId) REFERENCES comm.PageVersions (RowId);

/* Managed by the core module, as of 20.1.0
CREATE TABLE comm.EmailOptions
(
    EmailOptionId INT NOT NULL,
    EmailOption NVARCHAR(50),
    Type NVARCHAR(60) NOT NULL DEFAULT 'messages',

    CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
);

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (0, 'No Email');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (1, 'All conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (2, 'My conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations');

-- new file email notification options
INSERT INTO comm.emailOptions (EmailOptionId, EmailOption, Type) VALUES (512, 'No Email', 'files');
INSERT INTO comm.emailOptions (EmailOptionId, EmailOption, Type) VALUES (513, '15 minute digest', 'files');
INSERT INTO comm.emailOptions (EmailOptionId, EmailOption, Type) VALUES (514, 'Daily digest', 'files');

CREATE TABLE comm.EmailFormats
(
    EmailFormatId INT NOT NULL,
    EmailFormat NVARCHAR(20),

    CONSTRAINT PK_EmailFormats PRIMARY KEY (EmailFormatId)
);

INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat) VALUES (0, 'Plain Text');
INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat) VALUES (1, 'HTML');

CREATE TABLE comm.PageTypes
(
    PageTypeId INT NOT NULL,
    PageType NVARCHAR(20),

    CONSTRAINT PK_PageTypes PRIMARY KEY (PageTypeId)
);

INSERT INTO comm.PageTypes (PageTypeId, PageType) VALUES (0, 'Message');
INSERT INTO comm.PageTypes (PageTypeId, PageType) VALUES (1, 'Wiki');

CREATE TABLE comm.EmailPrefs
(
    Container ENTITYID,
    UserId USERID,
    EmailOptionId INT NOT NULL,
    EmailFormatId INT NOT NULL,
    PageTypeId INT NOT NULL,
    LastModifiedBy USERID,
    Type NVARCHAR(60) NOT NULL DEFAULT 'messages',
    SrcIdentifier NVARCHAR(100) NOT NULL,  -- allow subscriptions to multiple forums within a single container

    CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId, Type, SrcIdentifier),
    CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
    CONSTRAINT FK_EmailPrefs_EmailOptions FOREIGN KEY (EmailOptionId) REFERENCES comm.EmailOptions (EmailOptionId),
    CONSTRAINT FK_EmailPrefs_EmailFormats FOREIGN KEY (EmailFormatId) REFERENCES comm.EmailFormats (EmailFormatId),
    CONSTRAINT FK_EmailPrefs_PageTypes FOREIGN KEY (PageTypeId) REFERENCES comm.PageTypes (PageTypeId)
);
*/

-- Discussions can be private, constrained to a certain subset of users (like a Cc: line)
CREATE TABLE comm.UserList
(
    MessageId INT NOT NULL,
    UserId USERID NOT NULL,

    CONSTRAINT PK_UserList PRIMARY KEY (MessageId, UserId)
);

-- Improve performance of user list lookups for permission checking
CREATE INDEX IX_UserList_UserId ON comm.UserList(UserId);

CREATE TABLE comm.RSSFeeds
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,
    FeedName NVARCHAR(250) NULL,
    FeedURL NVARCHAR(1000) NOT NULL,
    LastRead DATETIME NULL,
    Content NVARCHAR(MAX),

    CONSTRAINT PK_RSSFeeds PRIMARY KEY (RowId),
    CONSTRAINT UQ_RSSFeeds UNIQUE CLUSTERED (Container, RowId)
);

CREATE TABLE comm.Tours
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Title NVARCHAR(500) NOT NULL,
  Description NVARCHAR(4000),
  Container ENTITYID NOT NULL,
  EntityId ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Json NVARCHAR(MAX),
  Mode INT NOT NULL DEFAULT 0,

  CONSTRAINT PK_ToursId PRIMARY KEY (RowId)
);
