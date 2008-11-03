/*
 * Copyright (c) 2008 LabKey Corporation
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
/* study-8.20-8.21.sql */

ALTER TABLE study.Study
    ADD SecurityType NVARCHAR(32)
GO

UPDATE study.Study
  SET SecurityType = 'ADVANCED'
  WHERE
  StudySecurity = 1

UPDATE study.Study
  SET SecurityType = 'EDITABLE_DATASETS'
  WHERE
  StudySecurity = 0 AND
  DatasetRowsEditable = 1

UPDATE study.Study
  SET SecurityType = 'BASIC'
  WHERE
  StudySecurity = 0 AND
  DatasetRowsEditable = 0

GO

declare @constname sysname
select @constname= so.name
from
sysobjects so inner join sysconstraints sc on (sc.constid = so.id)
inner join sysobjects soc on (sc.id = soc.id)
where so.xtype='D'
and soc.id=object_id('Study.Study')
and col_name(soc.id, sc.colid) = 'StudySecurity'

declare @cmd varchar(500)
select @cmd='Alter Table Study.Study DROP CONSTRAINT ' + @constname
select @cmd

exec(@cmd)

ALTER TABLE study.Study
  DROP COLUMN StudySecurity

select @constname= so.name
from
sysobjects so inner join sysconstraints sc on (sc.constid = so.id)
inner join sysobjects soc on (sc.id = soc.id)
where so.xtype='D'
and soc.id=object_id('Study.Study')
and col_name(soc.id, sc.colid) = 'DatasetRowsEditable'

select @cmd='Alter Table Study.Study DROP CONSTRAINT ' + @constname
select @cmd

exec(@cmd)

ALTER TABLE study.Study
  DROP COLUMN DatasetRowsEditable

GO  

ALTER TABLE study.Study
  ALTER COLUMN SecurityType NVARCHAR(32) NOT NULL

GO

/* study-8.21-8.22.sql */

ALTER TABLE study.Cohort
    ADD LSID NVARCHAR(200)
GO

/* study-8.22-8.23.sql */

UPDATE study.Cohort
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL
GO

ALTER TABLE
    study.Cohort
    ALTER COLUMN LSID NVARCHAR(200) NOT NULL
GO

ALTER TABLE study.Visit
    ADD LSID NVARCHAR(200)
GO

UPDATE study.Visit
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL
GO

ALTER TABLE
    study.Visit
    ALTER COLUMN LSID NVARCHAR(200) NOT NULL
GO

ALTER TABLE study.Study
    ADD LSID NVARCHAR(200)
GO

UPDATE study.Study
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL
GO

ALTER TABLE
    study.Study
    ALTER COLUMN LSID NVARCHAR(200) NOT NULL
GO

/* study-8.23-8.24.sql */

ALTER TABLE
    study.Study
    ADD ManualCohortAssignment BIT NOT NULL DEFAULT 0
GO

/* study-8.24-8.25.sql */

CREATE TABLE study.QCState
    (
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(64) NULL,
    Description NVARCHAR(500) NULL,
    Container ENTITYID NOT NULL,
    PublicData BIT NOT NULL,
    CONSTRAINT PK_QCState PRIMARY KEY (RowId),
    CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
    )
GO

ALTER TABLE study.StudyData ADD
    QCState INT NULL,
    CONSTRAINT FK_StudyData_QCState FOREIGN KEY (QCState) REFERENCES study.QCState (RowId)

CREATE INDEX IX_StudyData_QCState ON study.StudyData(QCState)
GO

ALTER TABLE study.Study ADD
    DefaultPipelineQCState INT,
    DefaultAssayQCState INT,
    DefaultDirectEntryQCState INT,
    ShowPrivateDataByDefault BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES study.QCState (RowId)
GO

/* study-8.25-8.26.sql */

ALTER TABLE
    study.Visit
    DROP COLUMN LSID
GO

/* study-8.26-8.27.sql */

UPDATE study.Study
  SET SecurityType = 'BASIC_READ'
  WHERE
  SecurityType = 'BASIC'

UPDATE study.Study
  SET SecurityType = 'BASIC_WRITE'
  WHERE
  SecurityType = 'EDITABLE_DATASETS'

UPDATE study.Study
  SET SecurityType = 'ADVANCED_READ'
  WHERE
  SecurityType = 'ADVANCED'

GO

/* study-8.27-8.28.sql */

CREATE TABLE study.SpecimenComment
       (
       RowId INT IDENTITY(1,1),
       Container ENTITYID NOT NULL,
       SpecimenNumber NVARCHAR(50) NOT NULL,
       GlobalUniqueId NVARCHAR(50) NOT NULL,
       CreatedBy USERID,
       Created DATETIME,
       ModifiedBy USERID,
       Modified DATETIME,
       Comment NTEXT,
       CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
       );

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenNumber ON study.SpecimenComment(SpecimenNumber);

/* study-8.28-8.29.sql */

UPDATE study.dataset
  SET label = name
WHERE
  label IS NULL;

ALTER TABLE study.dataset
  ALTER COLUMN label NVARCHAR(200) NOT NULL;

ALTER TABLE study.dataset ADD
  CONSTRAINT UQ_DatasetLabel UNIQUE (container, label);
GO

/* study-8.29-8.291.sql */

ALTER TABLE study.Specimen
  ADD SpecimenHash NVARCHAR(256);
GO

DROP INDEX study.SpecimenComment.IX_SpecimenComment_SpecimenNumber;
GO

ALTER TABLE study.SpecimenComment
    ADD SpecimenHash NVARCHAR(256);

ALTER TABLE study.SpecimenComment
    DROP COLUMN SpecimenNumber;
GO

CREATE INDEX IX_SpecimenComment_SpecimenHash ON study.SpecimenComment(Container, SpecimenHash);
CREATE INDEX IX_Specimen_SpecimenHash ON study.Specimen(Container, SpecimenHash);
GO