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

/* comm-0.00-1.10.sql */

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
	Parent INT NOT NULL,
	DisplayOrder FLOAT NOT NULL

	CONSTRAINT PK_Pages PRIMARY KEY (EntityId),
	CONSTRAINT UQ_Pages UNIQUE CLUSTERED (Container, Name)
)
GO

/* comm-1.20-1.30.sql */

CREATE TABLE comm.PageVersions
(
    RowId int IDENTITY (1, 1) NOT NULL,
    PageEntityId ENTITYID NOT NULL,
    CreatedBy USERID NULL,
    Created datetime NULL,
    Owner USERID NULL,
    Version int NOT NULL,
    Title nvarchar (255),
    Body ntext,

    CONSTRAINT PK_PageVersions PRIMARY KEY (RowId),
    CONSTRAINT FK_PageVersions_Pages FOREIGN KEY (PageEntityId) REFERENCES comm.Pages(EntityId),
    CONSTRAINT UQ_PageVersions UNIQUE (PageEntityId, Version)
)
GO

/* comm-1.30-1.40.sql */

ALTER TABLE comm.Pages ADD PageVersionId int NULL
ALTER TABLE comm.Pages ADD CONSTRAINT FK_Pages_PageVersions FOREIGN KEY (PageVersionId) REFERENCES comm.PageVersions (RowId)
GO

CREATE TABLE comm.EmailOptions
(
	EmailOptionId INT NOT NULL,
	EmailOption NVARCHAR(50),

	CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
)
GO

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (0, 'No Email')
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (1, 'All conversations')
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (2, 'My conversations')
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations')
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations')
GO

CREATE TABLE comm.EmailFormats
(
	EmailFormatId INT NOT NULL,
	EmailFormat NVARCHAR(20),

	CONSTRAINT PK_EmailFormats PRIMARY KEY (EmailFormatId)
)
GO

INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat) VALUES (0, 'Plain Text')
INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat) VALUES (1, 'HTML')


CREATE TABLE comm.PageTypes
(
	PageTypeId INT NOT NULL,
	PageType NVARCHAR(20),

	CONSTRAINT PK_PageTypes PRIMARY KEY (PageTypeId)
)
GO

INSERT INTO comm.PageTypes (PageTypeId, PageType) VALUES (0, 'Message')
INSERT INTO comm.PageTypes (PageTypeId, PageType) VALUES (1, 'Wiki')
GO

CREATE TABLE comm.EmailPrefs
(
	Container ENTITYID,
	UserId USERID,
	EmailOptionId INT NOT NULL,
	EmailFormatId INT NOT NULL,
	PageTypeId INT NOT NULL,

	CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId),
	CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
	CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
	CONSTRAINT FK_EmailPrefs_EmailOptions FOREIGN KEY (EmailOptionId) REFERENCES comm.EmailOptions (EmailOptionId),
	CONSTRAINT FK_EmailPrefs_EmailFormats FOREIGN KEY (EmailFormatId) REFERENCES comm.EmailFormats (EmailFormatId),
	CONSTRAINT FK_EmailPrefs_PageTypes FOREIGN KEY (PageTypeId) REFERENCES comm.PageTypes (PageTypeId)
)
GO

/* comm-1.40-1.50.sql */

ALTER TABLE comm.EmailPrefs ADD LastModifiedBy USERID
GO

/* comm-1.50-1.60.sql */

ALTER TABLE comm.PageVersions ADD RendererType NVARCHAR(50) NOT NULL DEFAULT 'RADEOX'
GO
ALTER TABLE comm.Announcements ADD RendererType NVARCHAR(50) NOT NULL DEFAULT 'RADEOX'
GO

/* comm-1.60-1.70.sql */

-- Discussions can be private, constrained to a certain subset of users (like a Cc: line)
CREATE TABLE comm.UserList
(
	MessageId INT NOT NULL,
	UserId USERID NOT NULL,

	CONSTRAINT PK_UserList PRIMARY KEY (MessageId, UserId)
)
GO

-- Improve performance of user list lookups for permission checking
CREATE INDEX IX_UserList_UserId ON comm.UserList(UserId)
GO

-- Add EmailList (place to store history of addresses that were notified), Status and AssignedTo
ALTER TABLE comm.Announcements
    ADD EmailList VARCHAR(1000) NULL, Status VARCHAR(50) NULL, AssignedTo USERID NULL
GO

-- Allow RenderType to be NULL; updates to properties will result in NULL body and NULL render type
ALTER TABLE comm.Announcements
    ALTER COLUMN RendererType NVARCHAR(50) NULL
GO

-- Drop unused Owner column
ALTER TABLE comm.Announcements
    DROP COLUMN Owner
GO

/* comm-1.70-2.00.sql */

ALTER TABLE comm.Announcements ADD DiscussionSrcIdentifier NVARCHAR(100) NULL
ALTER TABLE comm.Announcements ADD DiscussionSrcURL NVARCHAR(1000) NULL
GO

CREATE INDEX IX_DiscussionSrcIdentifier ON comm.announcements(Container, DiscussionSrcIdentifier)
GO
