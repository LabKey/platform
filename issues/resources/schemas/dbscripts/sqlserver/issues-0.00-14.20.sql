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
GO

CREATE TABLE issues.Issues
(
    _ts TIMESTAMP,
    Container ENTITYID NOT NULL,
    IssueId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID DEFAULT NEWID(),    -- used for attachments

    Title NVARCHAR(255) NOT NULL,
    Status NVARCHAR(8) NOT NULL,
    AssignedTo USERID NOT NULL,
    Type NVARCHAR(32),

    Area NVARCHAR(32),

    Priority INT NOT NULL DEFAULT 2,
    Milestone NVARCHAR(32),
    BuildFound NVARCHAR(32),

    ModifiedBy USERID NOT NULL,
    Modified DATETIME DEFAULT GETDATE(),

    CreatedBy USERID NOT NULL,
    Created DATETIME DEFAULT GETDATE(),
    Tag NVARCHAR(32),

    ResolvedBy USERID,
    Resolved DATETIME,
    Resolution NVARCHAR(32),
    Duplicate INT,

    ClosedBy USERID,
    Closed DATETIME,

    Int1 INT NULL,
    Int2 INT NULL,
    String1 VARCHAR(200) NULL,
    String2 VARCHAR(200) NULL,
    NotifyList TEXT,
    LastIndexed DATETIME NULL,

    CONSTRAINT PK_Issues PRIMARY KEY (IssueId)
);
CREATE INDEX IX_Issues_AssignedTo ON issues.Issues (AssignedTo);
CREATE INDEX IX_Issues_Status ON issues.Issues (Status);

CREATE TABLE issues.Comments
(
    --EntityId ENTITYID DEFAULT NEWID(),
    CommentId INT IDENTITY(1,1),
    IssueId INT,
    CreatedBy USERID,
    Created DATETIME DEFAULT GETDATE(),
    Comment NTEXT,
    EntityId ENTITYID,

    CONSTRAINT PK_Comments PRIMARY KEY (IssueId, CommentId),
    CONSTRAINT FK_Comments_Issues FOREIGN KEY (IssueId) REFERENCES issues.Issues(IssueId)
);


CREATE TABLE issues.IssueKeywords
(
    Container ENTITYID NOT NULL,
    Type INT NOT NULL,    -- area or milestone (or whatever)
    Keyword VARCHAR(255) NOT NULL,
    "Default" BIT NOT NULL DEFAULT 0,

    CONSTRAINT PK_IssueKeywords PRIMARY KEY (Container, Type, Keyword)
);


CREATE TABLE issues.EmailPrefs
(
    Container ENTITYID,
    UserId USERID,
    EmailOption INT NOT NULL,

    CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId),
    CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
);

/* issues-10.20-10.30.sql */

ALTER TABLE issues.Issues ADD
    String3 VARCHAR(200) NULL,
    String4 VARCHAR(200) NULL,
    String5 VARCHAR(200) NULL;

ALTER TABLE issues.Issues
    ALTER COLUMN Area NVARCHAR(200) NULL;

 ALTER TABLE issues.Issues
    ALTER COLUMN Milestone NVARCHAR(200) NULL;

 ALTER TABLE issues.Issues
    ALTER COLUMN Type NVARCHAR(200) NULL;
    
 ALTER TABLE issues.Issues
    ALTER COLUMN Resolution NVARCHAR(200) NULL;

/* issues-12.30-13.10.sql */

-- Move the column settings from properties to a proper table, add column permissions
CREATE TABLE issues.CustomColumns
(
    Container ENTITYID NOT NULL,
    Name VARCHAR(50) NOT NULL,
    Caption VARCHAR(200) NOT NULL,
    PickList BIT NOT NULL DEFAULT 0,
    Permission VARCHAR(300) NOT NULL,

    CONSTRAINT PK_CustomColumns PRIMARY KEY (Container, Name)
);

INSERT INTO issues.CustomColumns
    SELECT ObjectId AS Container, LOWER(Name), Value AS Caption,
        CASE WHEN CHARINDEX
        (
            Name, (SELECT Value FROM prop.PropertyEntries pl WHERE Category = 'IssuesCaptions'
             AND Name = 'pickListColumns' AND pe.ObjectId = pl.ObjectId)
        ) > 0 THEN 1 ELSE 0 END AS PickList, 'org.labkey.api.security.permissions.ReadPermission' AS Permission
    FROM prop.PropertyEntries pe WHERE Category = 'IssuesCaptions' AND Name <> 'pickListColumns';

-- These properties have been moved to a dedicated table
DELETE FROM prop.Properties WHERE "Set" IN (SELECT "Set" FROM prop.PropertySets WHERE Category = 'IssuesCaptions');
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

/* issues-14.10-14.20.sql */

DELETE FROM prop.properties WHERE name = 'experimentalFeature.issuesactivity';

CREATE TABLE issues.RelatedIssues
(
    IssueId INT,
    RelatedIssueId INT,

    CONSTRAINT PK_RelatedIssues PRIMARY KEY (IssueId, RelatedIssueId),
    CONSTRAINT FK_RelatedIssues_Issues_IssueId FOREIGN KEY (IssueId) REFERENCES issues.Issues(IssueId),
    CONSTRAINT FK_RelatedIssues_Issues_RelatedIssueId FOREIGN KEY (RelatedIssueId) REFERENCES issues.Issues(IssueId)
);
CREATE INDEX IX_RelatedIssues_IssueId ON issues.RelatedIssues (IssueId);
CREATE INDEX IX_RelatedIssues_RelatedIssueId ON issues.RelatedIssues (RelatedIssueId);