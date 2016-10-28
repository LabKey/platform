/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

ALTER TABLE issues.Issues
    ADD COLUMN String3 VARCHAR(200) NULL,
    ADD COLUMN String4 VARCHAR(200) NULL,
    ADD COLUMN String5 VARCHAR(200) NULL;

/* issues-11.20-11.30.sql */

ALTER TABLE issues.Issues
    ALTER COLUMN Area TYPE VARCHAR(200),
    ALTER COLUMN Type TYPE VARCHAR(200),
    ALTER COLUMN Milestone TYPE VARCHAR(200),
    ALTER COLUMN Resolution TYPE VARCHAR(200);

/* issues-12.30-13.10.sql */

-- Move the column settings from properties to a proper table, add column permissions
CREATE TABLE issues.CustomColumns
(
    Container ENTITYID NOT NULL,
    Name VARCHAR(50) NOT NULL,
    Caption VARCHAR(200) NOT NULL,
    PickList BOOLEAN NOT NULL DEFAULT '0',
    Permission VARCHAR(300) NOT NULL,

    CONSTRAINT PK_CustomColumns PRIMARY KEY (Container, Name)
);

INSERT INTO issues.CustomColumns
    SELECT ObjectId AS Container, LOWER(Name), Value AS Caption,
        STRPOS
        (
            (SELECT Value FROM prop.PropertyEntries pl WHERE Category = 'IssuesCaptions'
             AND Name = 'pickListColumns' AND pe.ObjectId = pl.ObjectId), Name
        ) > 0 AS PickList, 'org.labkey.api.security.permissions.ReadPermission' AS Permission
    FROM prop.PropertyEntries pe WHERE Category = 'IssuesCaptions' AND Name <> 'pickListColumns';

-- These properties have been moved to a dedicated table
DELETE FROM prop.Properties WHERE Set IN (SELECT Set FROM prop.PropertySets WHERE Category = 'IssuesCaptions');
DELETE FROM prop.PropertySets WHERE Category = 'IssuesCaptions';

UPDATE issues.issues SET type = NULL WHERE type = '';
UPDATE issues.issues SET area = NULL WHERE area = '';
UPDATE issues.issues SET milestone = NULL WHERE milestone = '';
UPDATE issues.issues SET resolution = NULL WHERE resolution = '';
UPDATE issues.issues SET string1 = NULL WHERE string1 = '';
UPDATE issues.issues SET string2 = NULL WHERE string2 = '';
UPDATE issues.issues SET string3 = NULL WHERE string3 = '';
UPDATE issues.issues SET string4 = NULL WHERE string4 = '';
UPDATE issues.issues SET string5 = NULL WHERE string5 = '';