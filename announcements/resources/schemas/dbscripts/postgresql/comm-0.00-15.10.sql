/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

/* comm-0.00-10.10.sql */

-- Create schema comm: tables for Announcements and Wiki

CREATE SCHEMA comm;

CREATE TABLE comm.Announcements
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Container ENTITYID NOT NULL,
    Parent ENTITYID,
    Title VARCHAR(255),
    Expires TIMESTAMP,
    Body TEXT,
    RendererType VARCHAR(50) NULL,       -- Updates to properties will result in NULL body and NULL render type
    EmailList VARCHAR(1000) NULL,        -- History of addresses that were notified
    Status VARCHAR(50) NULL,
    AssignedTo USERID NULL,
    DiscussionSrcIdentifier VARCHAR(100) NULL,
    DiscussionSrcURL VARCHAR(1000) NULL,
    LastIndexed TIMESTAMP NULL,

    CONSTRAINT PK_Announcements PRIMARY KEY (RowId),
    CONSTRAINT UQ_Announcements UNIQUE (Container, Parent, RowId)
);

CREATE INDEX IX_DiscussionSrcIdentifier ON comm.announcements(Container, DiscussionSrcIdentifier);

CREATE TABLE comm.Pages
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Owner USERID,
    Container ENTITYID NOT NULL,
    Name VARCHAR(255) NOT NULL,
    Parent INT NOT NULL,
    DisplayOrder REAL NOT NULL,
    PageVersionId INT4 NULL,
    ShowAttachments BOOLEAN NOT NULL DEFAULT TRUE,
    LastIndexed TIMESTAMP NULL,

    CONSTRAINT PK_Pages PRIMARY KEY (EntityId),
    CONSTRAINT UQ_Pages UNIQUE (Container, Name)
);

CREATE TABLE comm.PageVersions
(
    RowId SERIAL NOT NULL,
    PageEntityId ENTITYID NOT NULL,
    Created TIMESTAMP,
    CreatedBy USERID,
    Owner USERID,
    Version INT4 NOT NULL,
    Title VARCHAR(255),
    Body TEXT,
    RendererType VARCHAR(50) NOT NULL DEFAULT 'RADEOX',

    CONSTRAINT PK_PageVersions PRIMARY KEY (RowId),
    CONSTRAINT FK_PageVersions_Pages FOREIGN KEY (PageEntityId) REFERENCES comm.Pages(EntityId),
    CONSTRAINT UQ_PageVersions UNIQUE (PageEntityId, Version)
);

ALTER TABLE comm.Pages
    ADD CONSTRAINT FK_Pages_PageVersions FOREIGN KEY (PageVersionId) REFERENCES comm.PageVersions (RowId);

CREATE TABLE comm.EmailOptions
(
    EmailOptionId INT4 NOT NULL,
    EmailOption VARCHAR(50),

    CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
);

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (0, 'No Email');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (1, 'All conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (2, 'My conversations');
INSERT INTO comm.EmailOptions (EmailOptionID, EmailOption) VALUES (3, 'Broadcast only');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations');

CREATE TABLE comm.EmailFormats
(
    EmailFormatId INT4 NOT NULL,
    EmailFormat VARCHAR(20),

    CONSTRAINT PK_EmailFormats PRIMARY KEY (EmailFormatId)
);

INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat) VALUES (0, 'Plain Text');
INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat) VALUES (1, 'HTML');

CREATE TABLE comm.PageTypes
(
    PageTypeId INT4 NOT NULL,
    PageType VARCHAR(20),

    CONSTRAINT PK_PageTypes PRIMARY KEY (PageTypeId)
);

INSERT INTO comm.PageTypes (PageTypeId, PageType) VALUES (0, 'Message');
INSERT INTO comm.PageTypes (PageTypeId, PageType) VALUES (1, 'Wiki');

CREATE TABLE comm.EmailPrefs
(
    Container ENTITYID,
    UserId USERID,
    EmailOptionId INT4 NOT NULL,
    EmailFormatId INT4 NOT NULL,
    PageTypeId INT4 NOT NULL,
    LastModifiedBy USERID,

    CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId),
    CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
    CONSTRAINT FK_EmailPrefs_EmailOptions FOREIGN KEY (EmailOptionId) REFERENCES comm.EmailOptions (EmailOptionId),
    CONSTRAINT FK_EmailPrefs_EmailFormats FOREIGN KEY (EmailFormatId) REFERENCES comm.EmailFormats (EmailFormatId),
    CONSTRAINT FK_EmailPrefs_PageTypes FOREIGN KEY (PageTypeId) REFERENCES comm.PageTypes (PageTypeId)
);

-- Discussions can be private, constrained to a certain subset of users (like a Cc: line)
CREATE TABLE comm.UserList
(
    MessageId INT NOT NULL,
    UserId USERID NOT NULL,

    CONSTRAINT PK_UserList PRIMARY KEY (MessageId, UserId)
);

-- Improve performance of user list lookups for permission checking
CREATE INDEX IX_UserList_UserId ON comm.UserList(UserId);

/* comm-10.30-11.10.sql */

INSERT INTO comm.EmailOptions (EmailOptionID, EmailOption) VALUES (259, 'Daily digest of broadcast messages only');
UPDATE comm.EmailOptions SET EmailOption = 'Broadcast messages only' WHERE EmailOptionID = 3;

-- add a new column to contain 'notification type' information for both the
-- email prefs and options
ALTER TABLE comm.EmailOptions ADD COLUMN Type VARCHAR(60) NOT NULL DEFAULT 'messages';
ALTER TABLE comm.EmailPrefs ADD COLUMN Type VARCHAR(60) NOT NULL DEFAULT 'messages';

ALTER TABLE comm.EmailPrefs DROP CONSTRAINT PK_EmailPrefs;
ALTER TABLE comm.EmailPrefs ADD CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId, Type);

-- new file email notification options
INSERT INTO comm.emailOptions (EmailOptionId, EmailOption, Type) VALUES
  (512, 'No Email', 'files'),
  (513, '15 minute digest', 'files'),
  (514, 'Daily digest', 'files');

-- migrate existing file setting from property manager props
INSERT INTO comm.emailPrefs (Container, UserId, EmailOptionId, EmailFormatId, PageTypeId, Type) SELECT
	ObjectId,
	UserId,
	CAST(Value AS INTEGER) + 512,
	1, 0, 'files'
	FROM prop.Properties props JOIN prop.PropertySets ps on props.set = ps.set AND category = 'EmailService.emailPrefs' WHERE name = 'FileContentEmailPref' AND value <> '-1';

-- update folder default settings
UPDATE prop.Properties SET Value = '512' WHERE name = 'FileContentDefaultEmailPref' AND Value = '0';
UPDATE prop.Properties SET Value = '513' WHERE name = 'FileContentDefaultEmailPref' AND Value = '1';

-- delete old user property values
DELETE FROM prop.Properties WHERE name = 'FileContentEmailPref';

/* comm-11.10-11.20.sql */

-- Switch case-sensitive UNIQUE CONSTRAINTs to case-insensitive UNIQUE INDEXes

ALTER TABLE comm.Pages DROP CONSTRAINT UQ_Pages;

CREATE UNIQUE INDEX UQ_Pages ON comm.Pages (Container, LOWER(Name));

/* comm-11.30-12.10.sql */

-- Change all "Broadcast messages only" and "Daily digest of broadcast messages only" preferences
-- to "No Email", then remove the broadcast options.
UPDATE comm.EmailPrefs SET EmailOptionID = 0 WHERE EmailOptionID IN (3, 259);
DELETE FROM comm.EmailOptions WHERE EmailOptionID IN (3, 259);

-- add a new column to allow subscriptions to multiple forums within a single container
ALTER TABLE comm.EmailPrefs ADD COLUMN SrcIdentifier VARCHAR(100);
UPDATE comm.EmailPrefs SET SrcIdentifier = Container;
ALTER TABLE comm.EmailPrefs ALTER COLUMN SrcIdentifier SET NOT NULL;

ALTER TABLE comm.EmailPrefs DROP CONSTRAINT pk_emailprefs;
ALTER TABLE comm.EmailPrefs ADD CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId, Type, SrcIdentifier);

UPDATE comm.Announcements SET DiscussionSrcIdentifier = Container WHERE DiscussionSrcIdentifier IS NULL AND Parent IS NULL;

/* comm-12.10-12.20.sql */

ALTER TABLE comm.Pages ADD COLUMN ShouldIndex BOOLEAN DEFAULT TRUE;
UPDATE comm.Pages SET ShouldIndex=TRUE;

/* comm-14.10-14.20.sql */

CREATE TABLE comm.RSSFeeds
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Container ENTITYID NOT NULL,
    FeedName VARCHAR(250) NULL,
    FeedURL VARCHAR(1000) NOT NULL,
    LastRead TIMESTAMP NULL,
    Content TEXT,

    CONSTRAINT PK_RSSFeeds PRIMARY KEY (RowId),
    CONSTRAINT UQ_RSSFeeds UNIQUE (Container, RowId)
);

ALTER TABLE comm.announcements DROP COLUMN EmailList;

/* comm-14.30-14.31.sql */
CREATE TABLE comm.Tours
(
  RowId SERIAL,
  Title VARCHAR(500) NOT NULL,
  Description VARCHAR(4000),
  Container ENTITYID NOT NULL,
  EntityId ENTITYID NOT NULL,
  Created TIMESTAMP,
  CreatedBy USERID,
  Modified TIMESTAMP,
  ModifiedBy USERID,
  Json VARCHAR,
  Mode INT NOT NULL DEFAULT 0,

  CONSTRAINT PK_ToursId PRIMARY KEY (RowId)
);