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
	Title NVARCHAR(255),
	Body NTEXT,
	Parent INT NOT NULL,
	DisplayOrder FLOAT NOT NULL

	CONSTRAINT PK_Pages PRIMARY KEY (EntityId),
	CONSTRAINT UQ_Pages UNIQUE CLUSTERED (Container, Name)
	)
GO

/* comm-1.20-1.30.sql */

CREATE TABLE comm.Renderers
     (
     RowId INT IDENTITY(1,1) NOT NULL,
     Label NVARCHAR(50) NOT NULL,
     Name NVARCHAR(30) NOT NULL,
     CONSTRAINT PK_Renderers PRIMARY KEY (RowId)
     )
GO

INSERT INTO comm.Renderers (Label, Name) VALUES ('Radeox Engine', 'Radeox')
GO

CREATE TABLE comm.PageVersions
	(
	RowId int IDENTITY (1, 1) NOT NULL ,
	PageEntityId ENTITYID NOT NULL ,
	CreatedBy USERID NULL ,
	Created datetime NULL ,
	Owner USERID NULL ,
	Version int NOT NULL,
	RendererId int NOT NULL,
	Title nvarchar (255),
	Body ntext,

	CONSTRAINT PK_PageVersions PRIMARY KEY (RowId),
	CONSTRAINT FK_PageVersions_Pages FOREIGN KEY (PageEntityId) REFERENCES comm.Pages(EntityId),
	CONSTRAINT FK_PageVersions_Renderer FOREIGN KEY (RendererId) REFERENCES comm.Renderers(RowId),
	CONSTRAINT UQ_PageVersions UNIQUE (PageEntityId, Version)
	)
GO

INSERT INTO comm.PageVersions (PageEntityId, Title, Body, Created, CreatedBy, Owner, Version, RendererId)
     SELECT EntityId, Title, Body, Modified, ModifiedBy, Owner, 1, 1 FROM comm.Pages
GO

ALTER TABLE comm.Pages DROP COLUMN Title, Body
GO

/* comm-1.30-1.40.sql */

ALTER TABLE comm.Pages ADD PageVersionId int NULL
ALTER TABLE comm.Pages ADD CONSTRAINT FK_Pages_PageVersions FOREIGN KEY (PageVersionId) REFERENCES comm.PageVersions (RowId)
GO

UPDATE comm.Pages
SET comm.Pages.PageVersionId =
	(SELECT RowId
		FROM comm.PageVersions PV1
		WHERE comm.Pages.EntityId = PV1.PageEntityId
		AND PV1.Version =
			(SELECT MAX(Version)
			FROM comm.PageVersions PV2
			WHERE PV2.PageEntityId = comm.Pages.EntityId
			)
	)
GO

CREATE TABLE comm.EmailOptions
	(
	EmailOptionId INT NOT NULL,
	EmailOption NVARCHAR(50),
	CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
	)
GO

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption)
VALUES (0, 'No Email')

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption)
VALUES (1, 'All messages')

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption)
VALUES (2, 'Posted threads only')


CREATE TABLE comm.EmailFormats
	(
	EmailFormatId INT NOT NULL,
	EmailFormat NVARCHAR(20),
	CONSTRAINT PK_EmailFormats PRIMARY KEY (EmailFormatId)
	)
GO

INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat)
VALUES (0, 'Plain Text')

INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat)
VALUES (1, 'HTML')


CREATE TABLE comm.PageTypes
	(
	PageTypeId INT NOT NULL,
	PageType NVARCHAR(20),
	CONSTRAINT PK_PageTypes PRIMARY KEY (PageTypeId)
	)
GO

INSERT INTO comm.PageTypes (PageTypeId, PageType)
VALUES (0, 'Message')

INSERT INTO comm.PageTypes (PageTypeId, PageType)
VALUES (1, 'Wiki')
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

INSERT INTO comm.EmailPrefs (Container, UserId, EmailOptionId, EmailFormatId, PageTypeId)
    SELECT core.Containers.EntityId AS Container, prop.PropertySets.UserId, prop.Properties.Value, 1, 1
    FROM prop.Properties INNER JOIN
        prop.PropertySets ON prop.Properties.[Set] = prop.PropertySets.[Set] INNER JOIN
        core.Containers ON prop.PropertySets.ObjectId = core.Containers.EntityId INNER JOIN
        core.Users ON prop.PropertySets.UserId = core.Users.UserId
    WHERE (prop.PropertySets.Category = 'Announcements' AND prop.Properties.Name = 'email')
GO

DELETE FROM prop.Properties
WHERE prop.Properties.[Set] IN
    (SELECT [Set] FROM prop.PropertySets WHERE prop.PropertySets.Category = 'Announcements')
GO

DELETE FROM prop.PropertySets WHERE prop.PropertySets.Category = 'Announcements'
GO

/* comm-1.40-1.50.sql */

ALTER TABLE comm.EmailPrefs ADD LastModifiedBy USERID
GO

/* comm-1.50-1.60.sql */

ALTER TABLE comm.PageVersions ADD RendererType NVARCHAR(50) NOT NULL DEFAULT 'RADEOX'
GO
ALTER TABLE comm.Announcements ADD RendererType NVARCHAR(50) NOT NULL DEFAULT 'RADEOX'
GO

UPDATE comm.PageVersions SET RendererType = 'HTML' WHERE Body LIKE '<div%'
GO
UPDATE comm.Announcements SET RendererType = 'HTML' WHERE Body LIKE '<div%'
GO

ALTER TABLE comm.PageVersions DROP CONSTRAINT FK_PageVersions_Renderer
ALTER TABLE comm.PageVersions DROP COLUMN RendererId
GO

DROP TABLE comm.Renderers
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

UPDATE comm.Announcements
    SET Title = (SELECT Title FROM comm.Announcements p WHERE p.EntityId = comm.Announcements.Parent),
    Expires = (SELECT Expires FROM comm.Announcements p WHERE p.EntityId = comm.Announcements.Parent)
    WHERE Parent IS NOT NULL
GO

/* comm-1.70-2.00.sql */

ALTER TABLE comm.Announcements ADD DiscussionSrcIdentifier NVARCHAR(100) NULL
ALTER TABLE comm.Announcements ADD DiscussionSrcURL NVARCHAR(1000) NULL
GO

CREATE INDEX IX_DiscussionSrcIdentifier ON comm.announcements(Container, DiscussionSrcIdentifier)
GO

-- Better descriptions for existing email options
UPDATE comm.EmailOptions SET EmailOption = 'No email' WHERE EmailOptionId = 0
UPDATE comm.EmailOptions SET EmailOption = 'All conversations' WHERE EmailOptionId = 1
UPDATE comm.EmailOptions SET EmailOption = 'My conversations' WHERE EmailOptionId = 2
GO

-- Add new daily digest options
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations')
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations')
GO

-- Change folder defaults from 'None' to 'My conversations' (email is sent if you're on the member list or if you've posted to a conversation)
UPDATE prop.Properties SET Value = '2' WHERE
    "Set" IN (SELECT "Set" FROM prop.PropertySets WHERE Category = 'defaultEmailSettings' AND UserId = 0) AND
    Name = 'defaultEmailOption' AND
    Value = '0'
GO

-- Fix up mismatched containers for documents and announcements. 1.7 code inserted bad containers in some cases.
UPDATE core.documents SET container =
    (SELECT a.container FROM comm.announcements a WHERE
        a.entityid = core.documents.parent AND
        a.container != core.documents.container)
    WHERE
        core.documents.parent IN
            (SELECT a.entityid FROM comm.announcements a WHERE
                a.entityid = core.documents.parent AND
                a.container != core.documents.container)
GO

-- Fix up mismatched containers for documents and announcements. 1.7 code inserted bad containers in some cases.
UPDATE core.documents SET container =
    (SELECT p.container FROM comm.pages p WHERE
        p.entityid = core.documents.parent AND
        p.container != core.documents.container)
    WHERE
        core.documents.parent IN
            (SELECT p.entityid FROM comm.pages p WHERE
                p.entityid = core.documents.parent AND
                p.container != core.documents.container)
GO