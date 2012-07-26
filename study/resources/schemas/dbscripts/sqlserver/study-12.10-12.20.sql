/*
 * Copyright (c) 2012 LabKey Corporation
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

/* study-12.10-12.11.sql */

-- Rename 'ParticipantSequenceKey' to 'ParticipantSequenceNum' along with constraints and indices.
EXEC sp_rename 'study.ParticipantVisit.ParticipantSequenceKey', 'ParticipantSequenceNum', 'COLUMN';
EXEC sp_rename 'study.ParticipantVisit.UQ_StudyData_ParticipantSequenceKey', 'UQ_ParticipantVisit_ParticipantSequenceNum';
EXEC sp_rename 'study.ParticipantVisit.IX_ParticipantVisit_ParticipantSequenceKey', 'IX_ParticipantVisit_ParticipantSequenceNum', 'INDEX';
GO


EXEC sp_rename 'study.Specimen.ParticipantSequenceKey', 'ParticipantSequenceNum', 'COLUMN';
EXEC sp_rename 'study.Specimen.IX_Specimen_ParticipantSequenceKey', 'IX_Specimen_ParticipantSequenceNum', 'INDEX';
GO


EXEC core.executeJavaUpgradeCode 'renameDataSetParticipantSequenceKey';
GO

/* study-12.11-12.12.sql */

ALTER TABLE study.ParticipantGroup ADD
    Filter NTEXT NULL,
    Description NVARCHAR(250) NULL
GO

/* study-12.12-12.13.sql */

EXEC sp_RENAME 'study.ParticipantGroup.Filter', 'Filters', 'COLUMN';

/* study-12.14-12.15.sql */

ALTER TABLE study.study ADD DefaultTimepointDuration INT NOT NULL DEFAULT 1;

/* study-12.15-12.16.sql */

DELETE FROM study.study WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE study.Study
    ADD CONSTRAINT FK_Study_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

/* study-12.16-12.17.sql */

UPDATE study.StudyDesign SET sourceContainer=Container WHERE sourceContainer NOT IN (SELECT entityid FROM core.containers);

/* study-12.17-12.18.sql */

ALTER TABLE study.ParticipantGroup ADD CreatedBy USERID;
ALTER TABLE study.ParticipantGroup ADD Created DATETIME;
ALTER TABLE study.ParticipantGroup ADD ModifiedBy USERID;
ALTER TABLE study.ParticipantGroup ADD Modified DATETIME;
GO

UPDATE study.ParticipantGroup SET CreatedBy = ParticipantCategory.CreatedBy FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;
UPDATE study.ParticipantGroup SET Created = ParticipantCategory.Created FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;

UPDATE study.ParticipantGroup SET ModifiedBy = CreatedBy;
UPDATE study.ParticipantGroup SET Modified = Created;