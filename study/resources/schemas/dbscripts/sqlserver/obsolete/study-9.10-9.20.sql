/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

/* study-9.10-9.11.sql */

ALTER TABLE study.SpecimenComment ADD
  QualityControlFlag BIT NOT NULL DEFAULT 0,
  QualityControlFlagForced BIT NOT NULL DEFAULT 0,
  QualityControlComments NVARCHAR(512)
GO

ALTER TABLE study.SpecimenEvent ADD
  Ptid NVARCHAR(32),
  DrawTimestamp DATETIME,
  SalReceiptDate DATETIME,
  ClassId NVARCHAR(20),
  VisitValue NUMERIC(15,4),
  ProtocolNumber NVARCHAR(20),
  VisitDescription NVARCHAR(10),
  Volume double precision,
  VolumeUnits NVARCHAR(20),
  SubAdditiveDerivative NVARCHAR(50),
  PrimaryTypeId INT,
  DerivativeTypeId INT,
  AdditiveTypeId INT,
  DerivativeTypeId2 INT,
  OriginatingLocationId INT,
  FrozenTime DATETIME,
  ProcessingTime DATETIME,
  PrimaryVolume FLOAT,
  PrimaryVolumeUnits NVARCHAR(20)
GO

UPDATE study.SpecimenEvent SET
    study.SpecimenEvent.Ptid = study.Specimen.Ptid,
    study.SpecimenEvent.DrawTimestamp = study.Specimen.DrawTimestamp,
    study.SpecimenEvent.SalReceiptDate = study.Specimen.SalReceiptDate,
    study.SpecimenEvent.ClassId = study.Specimen.ClassId,
    study.SpecimenEvent.VisitValue = study.Specimen.VisitValue,
    study.SpecimenEvent.ProtocolNumber = study.Specimen.ProtocolNumber,
    study.SpecimenEvent.VisitDescription = study.Specimen.VisitDescription,
    study.SpecimenEvent.Volume = study.Specimen.Volume,
    study.SpecimenEvent.VolumeUnits = study.Specimen.VolumeUnits,
    study.SpecimenEvent.SubAdditiveDerivative = study.Specimen.SubAdditiveDerivative,
    study.SpecimenEvent.PrimaryTypeId = study.Specimen.PrimaryTypeId,
    study.SpecimenEvent.DerivativeTypeId = study.Specimen.DerivativeTypeId,
    study.SpecimenEvent.AdditiveTypeId = study.Specimen.AdditiveTypeId,
    study.SpecimenEvent.DerivativeTypeId2 = study.Specimen.DerivativeTypeId2,
    study.SpecimenEvent.OriginatingLocationId = study.Specimen.OriginatingLocationId,
    study.SpecimenEvent.FrozenTime = study.Specimen.FrozenTime,
    study.SpecimenEvent.ProcessingTime = study.Specimen.ProcessingTime,
    study.SpecimenEvent.PrimaryVolume = study.Specimen.PrimaryVolume,
    study.SpecimenEvent.PrimaryVolumeUnits = study.Specimen.PrimaryVolumeUnits
FROM study.Specimen WHERE study.Specimen.RowId = study.SpecimenEvent.SpecimenId
GO

/* study-9.11-9.12.sql */

CREATE INDEX IX_ParticipantVisit_Container ON study.ParticipantVisit(Container);
CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit(ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit(SequenceNum);
GO

ALTER TABLE study.SampleRequestSpecimen
  ADD Orphaned BIT NOT NULL DEFAULT 0
GO

UPDATE study.SampleRequestSpecimen SET Orphaned = 1 WHERE RowId IN (
    SELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen
    LEFT OUTER JOIN study.Specimen ON
        study.Specimen.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND
        study.Specimen.Container = study.SampleRequestSpecimen.Container
    WHERE study.Specimen.GlobalUniqueId IS NULL
)
GO

/* study-9.12-9.13.sql */

-- It was a mistake to make this a "hard" foreign key- query will take care of
-- linking without it, so we just need an index.  This allows us to drop all contents
-- of the table when reloading.
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives2
GO

CREATE INDEX IX_Specimens_Derivatives2 ON study.Specimen(DerivativeTypeId2)
GO

/* study-9.13-9.14.sql */

ALTER TABLE study.Specimen ADD
  AtRepository BIT NOT NULL DEFAULT 0,
  LockedInRequest BIT NOT NULL DEFAULT 0,
  Available BIT NOT NULL DEFAULT 0
GO

UPDATE study.Specimen SET AtRepository = 1
  WHERE CurrentLocation IN (SELECT ss.RowId FROM study.Site ss WHERE ss.Repository = 1)
GO

UPDATE study.Specimen SET LockedInRequest = 1 WHERE RowId IN
(
  SELECT study.Specimen.RowId FROM ((study.SampleRequest AS request
      JOIN study.SampleRequestStatus AS status ON request.StatusId = status.RowId AND status.SpecimensLocked = 1)
      JOIN study.SampleRequestSpecimen AS map ON request.rowid = map.SampleRequestId AND map.Orphaned = 0)
      JOIN study.Specimen ON study.Specimen.GlobalUniqueId = map.SpecimenGlobalUniqueId AND study.Specimen.Container = map.Container
)
GO

UPDATE study.Specimen SET Available =
(
  CASE Requestable
  WHEN 1 THEN (
      CASE LockedInRequest
      WHEN 1 THEN 0
      ELSE 1
      END)
  WHEN 0 THEN 0
  ELSE (
  CASE AtRepository
      WHEN 1 THEN (
          CASE LockedInRequest
          WHEN 1 THEN 0
          ELSE 1
          END)
      ELSE 0
      END)
  END
)
GO

/* study-9.14-9.15.sql */

ALTER TABLE study.Specimen ADD
  ProcessedByInitials NVARCHAR(32),
  ProcessingDate DATETIME,
  ProcessingLocation INT
GO

DROP INDEX study.SpecimenEvent.IX_SpecimenEvent_ShippedFromLab
GO
DROP INDEX study.SpecimenEvent.IX_SpecimenEvent_ShippedToLab
GO

ALTER TABLE study.SpecimenEvent ADD
  ProcessedByInitials NVARCHAR(32),
  ProcessingDate DATETIME
GO

ALTER TABLE study.SpecimenEvent DROP CONSTRAINT FK_ShippedFromLab_Site
GO
ALTER TABLE study.SpecimenEvent DROP CONSTRAINT FK_ShippedToLab_Site
GO

ALTER TABLE study.SpecimenEvent ALTER COLUMN ShippedFromLab NVARCHAR(32)
GO
ALTER TABLE study.SpecimenEvent ALTER COLUMN ShippedToLab NVARCHAR(32)
GO

/* study-9.18-9.19.sql */

ALTER TABLE study.Study ADD
  AllowReload BIT NOT NULL DEFAULT 0,
  ReloadInterval INT NULL,
  LastReload DATETIME NULL;

/* study-9.19-9.191.sql */

ALTER TABLE study.Study ADD
  ReloadUser UserId;

/* study-9.191-9.192.sql */

-- This script creates a hard table to hold static specimen data.  Dynamic data (available counts, etc)
-- is calculated on the fly via aggregates.

UPDATE study.Specimen SET SpecimenHash =
(SELECT
    'Fld-' + CAST(core.Containers.RowId AS NVARCHAR)
    +'~'+ CASE WHEN OriginatingLocationId IS NOT NULL THEN CAST(OriginatingLocationId AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN Ptid IS NOT NULL THEN CAST(Ptid AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN DrawTimestamp IS NOT NULL THEN CAST(DrawTimestamp AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN SalReceiptDate IS NOT NULL THEN CAST(SalReceiptDate AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN ClassId IS NOT NULL THEN CAST(ClassId AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN VisitValue IS NOT NULL THEN CAST(VisitValue AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN ProtocolNumber IS NOT NULL THEN CAST(ProtocolNumber AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN PrimaryVolume IS NOT NULL THEN CAST(PrimaryVolume AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN PrimaryVolumeUnits IS NOT NULL THEN CAST(PrimaryVolumeUnits AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN VisitDescription IS NOT NULL THEN CAST(VisitDescription AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN VolumeUnits IS NOT NULL THEN CAST(VolumeUnits AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN SubAdditiveDerivative IS NOT NULL THEN CAST(SubAdditiveDerivative AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN PrimaryTypeId IS NOT NULL THEN CAST(PrimaryTypeId AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN DerivativeTypeId IS NOT NULL THEN CAST(DerivativeTypeId AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN DerivativeTypeId2 IS NOT NULL THEN CAST(DerivativeTypeId2 AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN AdditiveTypeId IS NOT NULL THEN CAST(AdditiveTypeId AS NVARCHAR) ELSE '' END
FROM study.Specimen InnerSpecimen
JOIN core.Containers ON InnerSpecimen.Container = core.Containers.EntityId
WHERE InnerSpecimen.RowId = study.Specimen.RowId)
GO

UPDATE study.SpecimenComment SET SpecimenHash =
    (SELECT SpecimenHash FROM study.Specimen
    WHERE study.SpecimenComment.Container = study.Specimen.Container AND
          study.SpecimenComment.GlobalUniqueId = study.Specimen.GlobalUniqueId)
GO

-- First, we rename 'specimen' to 'vial' to correct a long-standing bad name
ALTER TABLE study.Specimen
    DROP CONSTRAINT FK_SpecimenOrigin_Site
GO

DROP INDEX study.Specimen.IX_Specimen_AdditiveTypeId
DROP INDEX study.Specimen.IX_Specimen_DerivativeTypeId
DROP INDEX study.Specimen.IX_Specimen_OriginatingLocationId
DROP INDEX study.Specimen.IX_Specimen_PrimaryTypeId
DROP INDEX study.Specimen.IX_Specimen_Ptid
DROP INDEX study.Specimen.IX_Specimen_VisitValue
DROP INDEX study.Specimen.IX_Specimens_Derivatives2

-- First, we rename 'specimen' to 'vial' to correct a long-standing bad name
EXEC sp_rename 'study.Specimen', 'Vial'

EXEC sp_rename 'study.Vial.IX_Specimen_Container', 'IX_Vial_Container', 'INDEX'
EXEC sp_rename 'study.Vial.IX_Specimen_CurrentLocation', 'IX_Vial_CurrentLocation', 'INDEX'
EXEC sp_rename 'study.Vial.IX_Specimen_GlobalUniqueId', 'IX_Vial_GlobalUniqueId', 'INDEX'
EXEC sp_rename 'study.Vial.IX_Specimen_SpecimenHash', 'IX_Vial_Container_SpecimenHash', 'INDEX'


-- Next, we create the specimen table, which will hold static properties of a specimen draw (versus a vial)
CREATE TABLE study.Specimen
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SpecimenHash NVARCHAR(256),
    Ptid NVARCHAR(32),
    VisitDescription NVARCHAR(10),
    VisitValue NUMERIC(15,4),
    VolumeUnits NVARCHAR(20),
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits NVARCHAR(20),
    PrimaryTypeId INTEGER,
    AdditiveTypeId INTEGER,
    DerivativeTypeId INTEGER,
    DerivativeTypeId2 INTEGER,
    SubAdditiveDerivative NVARCHAR(50),
    DrawTimestamp DATETIME,
    SalReceiptDate DATETIME,
    ClassId NVARCHAR(20),
    ProtocolNumber NVARCHAR(20),
    OriginatingLocationId INTEGER,
    TotalVolume FLOAT,
    AvailableVolume FLOAT,
    VialCount INTEGER,
    LockedInRequestCount INTEGER,
    AtRepositoryCount INTEGER,
    AvailableCount INTEGER,
    ExpectedAvailableCount INTEGER,
    CONSTRAINT PK_Specimen PRIMARY KEY (RowId)
)
GO

CREATE INDEX IX_Specimen_AdditiveTypeId ON study.Specimen(AdditiveTypeId)
CREATE INDEX IX_Specimen_Container ON study.Specimen(Container)
CREATE INDEX IX_Specimen_DerivativeTypeId ON study.Specimen(DerivativeTypeId)
CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId)
CREATE INDEX IX_Specimen_PrimaryTypeId ON study.Specimen(PrimaryTypeId)
CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid)
CREATE INDEX IX_Specimen_Container_SpecimenHash ON study.Specimen(Container, SpecimenHash)
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue)
CREATE INDEX IX_Specimen_DerivativeTypeId2 ON study.Specimen(DerivativeTypeId2)
GO

-- we populate 'specimen' via a grouped query over the vial table to retrive the constant properties:
INSERT INTO study.Specimen (Container, SpecimenHash, Ptid, VisitDescription, VisitValue,
        VolumeUnits, PrimaryVolume, PrimaryVolumeUnits, PrimaryTypeId,
        AdditiveTypeId, DerivativeTypeId, DerivativeTypeId2, SubAdditiveDerivative,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OriginatingLocationId)
    SELECT Container, SpecimenHash, Ptid, VisitDescription, VisitValue,
        VolumeUnits, PrimaryVolume, PrimaryVolumeUnits, PrimaryTypeId,
        AdditiveTypeId, DerivativeTypeId, DerivativeTypeId2, SubAdditiveDerivative,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OriginatingLocationId
    FROM study.Vial
    GROUP BY Container, SpecimenHash, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        PrimaryVolume, PrimaryVolumeUnits, DerivativeTypeId2,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
        OriginatingLocationId
GO

-- after specimen is populated, we create a foreign key column on vial, populate it, and change the type to NOT NULL
ALTER TABLE study.Vial ADD SpecimenId INTEGER;
GO

UPDATE study.Vial SET SpecimenId = (
    SELECT RowId FROM study.Specimen
    WHERE study.Specimen.SpecimenHash = study.Vial.SpecimenHash AND
        study.Specimen.Container = study.Vial.Container
);

ALTER TABLE study.Vial ALTER COLUMN SpecimenId INTEGER NOT NULL
GO

CREATE INDEX IX_Vial_SpecimenId ON study.Vial(SpecimenId)
GO

ALTER TABLE study.Vial ADD CONSTRAINT FK_Vial_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
GO

ALTER TABLE study.Vial DROP
    COLUMN Ptid,
    COLUMN VisitDescription,
    COLUMN VisitValue,
    COLUMN VolumeUnits,
    COLUMN PrimaryTypeId,
    COLUMN AdditiveTypeId,
    COLUMN DerivativeTypeId,
    COLUMN PrimaryVolume,
    COLUMN PrimaryVolumeUnits,
    COLUMN DerivativeTypeId2,
    COLUMN DrawTimestamp,
    COLUMN SalReceiptDate,
    COLUMN ClassId,
    COLUMN ProtocolNumber,
    COLUMN SubAdditiveDerivative,
    COLUMN OriginatingLocationId
GO

-- Update the cached counts on the specimen table
UPDATE study.Specimen SET
    TotalVolume = VialCounts.TotalVolume,
    AvailableVolume = VialCounts.AvailableVolume,
    VialCount = VialCounts.VialCount,
    LockedInRequestCount = VialCounts.LockedInRequestCount,
    AtRepositoryCount = VialCounts.AtRepositoryCount,
    AvailableCount = VialCounts.AvailableCount,
    ExpectedAvailableCount = VialCounts.ExpectedAvailableCount
FROM (SELECT
    Container, SpecimenHash,
    SUM(Volume) AS TotalVolume,
        SUM(CASE Available WHEN 1 THEN Volume ELSE 0 END) AS AvailableVolume,
        COUNT(GlobalUniqueId) AS VialCount,
        SUM(CASE LockedInRequest WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN 1 THEN 1 ELSE 0 END) AS AvailableCount,
        (COUNT(GlobalUniqueId) - SUM(CASE LockedInRequest WHEN 1 THEN 1 ELSE 0 END) - SUM(CASE Requestable WHEN 0 THEN 1 ELSE 0 END)) AS ExpectedAvailableCount
    FROM study.Vial
    GROUP BY Container, SpecimenHash
    ) VialCounts
WHERE study.Specimen.Container = VialCounts.Container AND study.Specimen.SpecimenHash = VialCounts.SpecimenHash
GO

/* study-9.192-9.193.sql */

ALTER TABLE study.Vial DROP
  COLUMN FrozenTime,
  COLUMN ProcessingTime,
  COLUMN ProcessedByInitials,
  COLUMN ProcessingDate
GO

ALTER TABLE study.Vial ADD
  PrimaryVolume FLOAT,
  PrimaryVolumeUnits NVARCHAR(20)
GO

UPDATE study.Vial SET
  PrimaryVolume = study.Specimen.PrimaryVolume,
  PrimaryVolumeUnits = study.Specimen.PrimaryVolumeUnits
FROM study.Specimen
WHERE study.Specimen.RowId = study.Vial.SpecimenId
GO

ALTER TABLE study.Specimen DROP
  COLUMN PrimaryVolume,
  COLUMN PrimaryVolumeUnits
GO

/* study-9.193-9.194.sql */

EXEC sp_rename 'study.SpecimenEvent.SpecimenId', 'VialId', 'COLUMN'
GO

/* study-9.194-9.195.sql */

CREATE INDEX IDX_StudyData_ContainerKey ON study.StudyData(Container, _Key)
GO