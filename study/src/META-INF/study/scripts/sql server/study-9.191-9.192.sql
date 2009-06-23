/*
 * Copyright (c) 2009 LabKey Corporation
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

UPDATE study.SpecimenComment SET SpecimenHash =
	(SELECT SpecimenHash FROM study.Specimen
	WHERE study.SpecimenComment.Container = study.Specimen.Container AND
          study.SpecimenComment.GlobalUniqueId = study.Specimen.GlobalUniqueId);

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
CREATE TABLE study.Specimen (
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
WHERE study.Specimen.Container = VialCounts.Container AND study.Specimen.SpecimenHash = VialCounts.SpecimenHash;

-- Finally update hash codes for all specimens, vials, and comments

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

UPDATE study.Vial SET SpecimenHash =
	(SELECT SpecimenHash FROM study.Specimen WHERE study.Vial.SpecimenId = study.Specimen.RowId);

UPDATE study.SpecimenComment SET SpecimenHash =
	(SELECT SpecimenHash FROM study.Vial
	WHERE study.SpecimenComment.Container = study.Vial.Container AND study.SpecimenComment.GlobalUniqueId = study.Vial.GlobalUniqueId);