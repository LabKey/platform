-- noinspection SqlNoDataSourceInspectionForFile

/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
    Duplicate INT,
    LastIndexed TIMESTAMP NULL,

    CONSTRAINT PK_Issues PRIMARY KEY (IssueId)
);

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

/* issues-16.10-16.20.sql */

CREATE SCHEMA IssueDef;

CREATE TABLE issues.IssueDef
(
    RowId SERIAL NOT NULL,
    Name VARCHAR(200) NOT NULL,

    Container ENTITYID NOT NULL,
    Created TIMESTAMP,
    Modified TIMESTAMP,
    CreatedBy INTEGER,
    ModifiedBy INTEGER,

    CONSTRAINT PK_IssueDef PRIMARY KEY (RowId),
    CONSTRAINT UQ_IssueDef_Container_Name UNIQUE (Name, Container)
);

-- Rename from IssueDef to IssueListDef
ALTER TABLE issues.IssueDef RENAME TO IssueListDef;

ALTER TABLE issues.Issues ADD COLUMN IssueDefId INTEGER;
ALTER TABLE issues.Issues ADD CONSTRAINT FK_IssueListDef_IssueDefId_RowId FOREIGN KEY (IssueDefId) REFERENCES issues.IssueListDef(RowId);

ALTER TABLE issues.IssueListDef ADD COLUMN Label VARCHAR(200);
UPDATE issues.IssueListDef SET Label = Name;
ALTER TABLE issues.IssueListDef ALTER COLUMN Label SET NOT NULL;

/* issues-16.20-16.30.sql */

ALTER TABLE issues.issuelistdef ADD COLUMN kind VARCHAR(200) NOT NULL DEFAULT 'IssueDefinition';