/*
 * Copyright (c) 2011 LabKey Corporation
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

/* study-11.10-11.101.sql */

-- NOTE: The renameObjectIdToRowId call was added as a late patch to 11.1. This script is for
-- servers that installed 11.1 before the patch was applied (their study module version is 11.10).

EXEC core.executeJavaUpgradeCode 'renameObjectIdToRowId'
GO

/* study-11.101-11.11.sql */

CREATE TABLE study.VisitAliases
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(200) NOT NULL,
    SequenceNum NUMERIC(15, 4) NOT NULL,

    CONSTRAINT PK_VisitNames PRIMARY KEY (RowId)
);

CREATE UNIQUE INDEX UQ_VisitAliases_Name ON study.VisitAliases (Container, Name);

/* study-11.12-11.13.sql */

-- named sets of normalization factors
CREATE TABLE study.ParticipantClassifications
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    Container ENTITYID NOT NULL,

	Label NVARCHAR(200) NOT NULL,
	Type NVARCHAR(60) NOT NULL,
    Shared BIT,
    AutoUpdate BIT,

	-- for queries
    QueryName NVARCHAR(200),
    ViewName NVARCHAR(200),
    SchemaName NVARCHAR(50),

    -- for cohorts
    DatasetId INT,
    GroupProperty NVARCHAR(200),

    CONSTRAINT pk_participantClassifications PRIMARY KEY (RowId)
)
GO

-- represents a grouping category for a participant classification
CREATE TABLE study.ParticipantGroup
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,

	Label NVARCHAR(200) NOT NULL,
    ClassificationId INT NOT NULL,

    CONSTRAINT pk_participantGroup PRIMARY KEY (RowId),
    CONSTRAINT fk_participantClassifications_classificationId FOREIGN KEY (ClassificationId) REFERENCES study.ParticipantClassifications (RowId)
)
GO

-- maps participants to participant groups
CREATE TABLE study.ParticipantGroupMap
(
    GroupId INT NOT NULL,
    ParticipantId NVARCHAR(32) NOT NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT pk_participantGroupMap PRIMARY KEY (GroupId, ParticipantId, Container),
    CONSTRAINT fk_participantGroup_groupId FOREIGN KEY (GroupId) REFERENCES study.ParticipantGroup (RowId),
    CONSTRAINT fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
)
GO

/* study-11.13-11.14.sql */

ALTER TABLE study.ParticipantClassifications DROP COLUMN Created
GO
ALTER TABLE study.ParticipantClassifications ADD Created DATETIME
GO

ALTER TABLE study.ParticipantGroupMap DROP CONSTRAINT fk_participant_participantId_container
GO

ALTER TABLE study.ParticipantGroupMap ADD CONSTRAINT
	fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
	ON DELETE CASCADE
GO

/* study-11.14-11.15.sql */

ALTER TABLE study.SpecimenEvent ADD TubeType NVARCHAR(32)
GO

ALTER TABLE study.Vial ADD TubeType NVARCHAR(32)
GO

/* study-11.15-11.16.sql */

-- Rename from ParticipantClassifications to ParticipantCategory (Singular)
EXEC sp_rename 'study.ParticipantClassifications', 'ParticipantCategory'
GO

-- Drop Foreign Key constraint
ALTER TABLE study.ParticipantGroup
	DROP CONSTRAINT fk_participantClassifications_classificationId
GO

-- Drop Primary Key constraint
ALTER TABLE study.ParticipantCategory
	DROP CONSTRAINT pk_participantClassifications
GO

-- Add Primary Key constraint
ALTER TABLE study.ParticipantCategory
	ADD CONSTRAINT pk_participantCategory PRIMARY KEY (RowId)
GO

-- Rename foreign key column
EXEC sp_rename 'study.ParticipantGroup.ClassificationId', 'CategoryId', 'COLUMN'
GO

-- Add Foreign Key constraint
ALTER TABLE study.ParticipantGroup
	ADD CONSTRAINT fk_participantCategory_categoryId FOREIGN KEY (CategoryId) REFERENCES study.ParticipantCategory (RowId)
GO

-- Add Unique constraint
ALTER TABLE study.ParticipantCategory
	ADD CONSTRAINT uq_Label_Container UNIQUE (Label, Container)
GO

/* study-11.16-11.17.sql */

-- named sets of normalization factors
ALTER TABLE study.ParticipantCategory ADD ModifiedBy USERID
GO
ALTER TABLE study.ParticipantCategory ADD Modified DATETIME
GO
UPDATE study.ParticipantCategory SET ModifiedBy = CreatedBy
GO
UPDATE study.ParticipantCategory SET Modified = Created
GO