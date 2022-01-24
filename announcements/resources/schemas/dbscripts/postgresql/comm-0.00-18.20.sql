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
    Status VARCHAR(50) NULL,
    AssignedTo USERID NULL,
    DiscussionSrcIdentifier VARCHAR(100) NULL,
    DiscussionSrcURL VARCHAR(1000) NULL,
    LastIndexed TIMESTAMP NULL,
    Approved TIMESTAMP NULL,

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
    ShouldIndex BOOLEAN DEFAULT TRUE,

    CONSTRAINT PK_Pages PRIMARY KEY (EntityId)
);

-- Need a case-insensitive UNIQUE INDEX on Name
CREATE UNIQUE INDEX UQ_Pages ON comm.Pages (Container, LOWER(Name));

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
