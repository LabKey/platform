/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

/* issues-0.00-10.20.sql */

CREATE SCHEMA issues;

CREATE TABLE issues.Issues
(
    _ts TIMESTAMP DEFAULT now(),
    Container ENTITYID NOT NULL,
    IssueId SERIAL,
    EntityId ENTITYID NOT NULL,

    Title VARCHAR(255) NOT NULL,
    Status VARCHAR(8) NOT NULL,
    AssignedTo USERID NOT NULL,
    Type VARCHAR(32),

    --Product VARCHAR(32), -- implied by parent?
    Area VARCHAR(32),
    --SubArea VARCHAR(32) -- nah

    Priority INT NOT NULL DEFAULT 2,
    --Severity INT NOT NULL DEFAULT 2, -- let's just use Priority for now
    Milestone VARCHAR(32),
    BuildFound VARCHAR(32),

    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP DEFAULT now(),

    CreatedBy USERID NOT NULL,
    Created TIMESTAMP DEFAULT now(),
    Tag VARCHAR(32),

    ResolvedBy USERID,
    Resolved TIMESTAMP,
    Resolution VARCHAR(32),
    Duplicate INT,

    ClosedBy USERID,
    Closed TIMESTAMP,

    Int1 INT NULL,
    Int2 INT NULL,
    String1 VARCHAR(200) NULL,
    String2 VARCHAR(200) NULL,
    NotifyList TEXT,
    LastIndexed TIMESTAMP NULL,

    CONSTRAINT PK_Issues PRIMARY KEY (IssueId)
);
CREATE INDEX IX_Issues_AssignedTo ON issues.Issues (AssignedTo);
CREATE INDEX IX_Issues_Status ON issues.Issues (Status);

CREATE TABLE issues.Comments
(
    EntityId ENTITYID,
    CommentId SERIAL,
    IssueId INT,
    CreatedBy USERID,
    Created TIMESTAMP DEFAULT now(),
    Comment TEXT,

    CONSTRAINT PK_Comments PRIMARY KEY (IssueId, CommentId),
    CONSTRAINT FK_Comments_Issues FOREIGN KEY (IssueId) REFERENCES issues.Issues(IssueId)
);

CREATE TABLE issues.IssueKeywords
(
    Container ENTITYID NOT NULL,
    Type INT NOT NULL,    -- area or milestone (or whatever)
    Keyword VARCHAR(255) NOT NULL,
    "default" BOOLEAN NOT NULL DEFAULT '0',

    CONSTRAINT PK_IssueKeywords PRIMARY KEY (Container, Type, Keyword)
);

CREATE TABLE issues.EmailPrefs
(
    Container ENTITYID,
    UserId USERID,
    EmailOption INT4 NOT NULL,

    CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId),
    CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId)
);

/* issues-10.20-10.30.sql */

/* issues-10.20-10.21.sql */

ALTER TABLE issues.Issues
    ADD COLUMN String3 VARCHAR(200) NULL,
    ADD COLUMN String4 VARCHAR(200) NULL,
    ADD COLUMN String5 VARCHAR(200) NULL;