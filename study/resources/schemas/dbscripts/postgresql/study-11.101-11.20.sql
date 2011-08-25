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

-- NOTE: This script is for servers that installed 11.1 after the 11.10-11.101 patch (calling
-- renameObjectIdToRowId) was added; these servers have a study module at version 11.101.

/* study-11.101-11.11.sql */

CREATE TABLE study.VisitAliases
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NOT NULL,
    SequenceNum NUMERIC(15, 4) NOT NULL,

    CONSTRAINT PK_VisitNames PRIMARY KEY (RowId)
);

CREATE UNIQUE INDEX UQ_VisitAliases_Name ON study.VisitAliases (Container, LOWER(Name));

/* study-11.11-11.12.sql */

-- Switch case-sensitive UNIQUE CONSTRAINTs to case-insensitive UNIQUE INDEXes

ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetName;
ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetLabel;

SELECT core.executeJavaUpgradeCode('uniquifyDatasetNamesAndLabels');

CREATE UNIQUE INDEX UQ_DatasetName ON study.Dataset (Container, LOWER(Name));
CREATE UNIQUE INDEX UQ_DatasetLabel ON study.Dataset (Container, LOWER(Label));

/* study-11.12-11.13.sql */

-- named sets of normalization factors
CREATE TABLE study.ParticipantClassifications
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    Container ENTITYID NOT NULL,

	Label VARCHAR(200) NOT NULL,
	Type VARCHAR(60) NOT NULL,
    Shared boolean,
    AutoUpdate boolean,

	-- for queries
    QueryName VARCHAR(200),
    ViewName VARCHAR(200),
    SchemaName VARCHAR(50),

    -- for cohorts
    DatasetId Integer,
    GroupProperty VARCHAR(200),

    CONSTRAINT pk_participantClassifications PRIMARY KEY (RowId)
);

-- represents a grouping category for a participant classification
CREATE TABLE study.ParticipantGroup
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,

	Label VARCHAR(200) NOT NULL,
    ClassificationId Integer NOT NULL,

    CONSTRAINT pk_participantGroup PRIMARY KEY (RowId),
    CONSTRAINT fk_participantClassifications_classificationId FOREIGN KEY (ClassificationId) REFERENCES study.ParticipantClassifications (RowId)
);

-- maps participants to participant groups
CREATE TABLE study.ParticipantGroupMap
(
    GroupId Integer NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT pk_participantGroupMap PRIMARY KEY (GroupId, ParticipantId, Container),
    CONSTRAINT fk_participantGroup_groupId FOREIGN KEY (GroupId) REFERENCES study.ParticipantGroup (RowId),
    CONSTRAINT fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant (Container, ParticipantId)
);

/* study-11.13-11.14.sql */

ALTER TABLE study.ParticipantGroupMap DROP CONSTRAINT fk_participant_participantId_container;

ALTER TABLE study.ParticipantGroupMap ADD CONSTRAINT
	fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
	ON DELETE CASCADE;

/* study-11.14-11.15.sql */

ALTER TABLE study.SpecimenEvent ADD TubeType VARCHAR(32);
ALTER TABLE study.Vial ADD TubeType VARCHAR(32);

/* study-11.15-11.16.sql */

-- Rename from ParticipantClassifications to ParticipantCategory (Singular)
ALTER TABLE study.participantclassifications
RENAME TO participantcategory;

-- Drop Foreign Key constraint
ALTER TABLE study.participantgroup
DROP CONSTRAINT fk_participantclassifications_classificationid;

-- Drop Primary Key constraint
ALTER TABLE study.participantcategory
DROP CONSTRAINT pk_participantclassifications;

-- Add Primary Key constraint
ALTER TABLE study.participantcategory
ADD CONSTRAINT pk_participantcategory PRIMARY KEY (rowid);

-- Rename foreign key column
ALTER TABLE study.participantgroup RENAME COLUMN classificationid TO categoryid;

-- Add Foreign Key constraint
ALTER TABLE study.participantgroup
ADD CONSTRAINT fk_participantcategory_categoryid FOREIGN KEY (categoryid)
REFERENCES study.participantcategory (rowid);

-- Add Unique constraint
ALTER TABLE study.participantcategory
ADD CONSTRAINT uq_label_container UNIQUE (label, container);

/* study-11.16-11.17.sql */

-- named sets of normalization factors
ALTER TABLE study.ParticipantCategory ADD ModifiedBy USERID;
ALTER TABLE study.ParticipantCategory ADD Modified TIMESTAMP;

UPDATE study.ParticipantCategory SET ModifiedBy = CreatedBy;
UPDATE study.ParticipantCategory SET Modified = Created;