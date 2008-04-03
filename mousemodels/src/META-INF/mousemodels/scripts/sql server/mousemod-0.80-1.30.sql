/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

EXEC sp_addapprole 'mousemod', 'password'
GO

CREATE TABLE mousemod.Genotype (
	GenotypeId int IDENTITY (1, 1),
	Genotype nvarchar (7)  NULL,
	container ENTITYID,			--lookupContainer
	CONSTRAINT PK_Genotype PRIMARY KEY (GenotypeId)
    )
GO

CREATE TABLE mousemod.IrradDose (
	IrradDoseId int IDENTITY (1, 1),
	IrradDose nvarchar (30)  NULL ,
	SortOrder int NULL DEFAULT 0,
	container ENTITYID,			--lookupContainer
	CONSTRAINT PK_IrradDose PRIMARY KEY (IrradDoseId)
)
GO

CREATE TABLE mousemod.MouseModel (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,

    modelId INT IDENTITY (1,1),
    entityId ENTITYID NOT NULL,
	container ENTITYID,			--container/path
    name nvarchar(63) NOT NULL,
    emiceId nvarchar (127)  NULL,
    tumorType nvarchar(63) NOT NULL,
    targetGeneId int NULL,
    mouseStrainId int NULL,
    metastasis bit NOT NULL DEFAULT 0,
    penetrance nvarchar(30) NOT NULL,
    latency nvarchar(30) NOT NULL,
    location nvarchar(30) NOT NULL,
    investigator nvarchar(127) NOT NULL,
	GenotypeId int NULL ,
	IrradDoseId int NULL ,
	TreatmentId int NULL ,
	PreNecroChem nvarchar (30)  NULL ,
	BrduAtDeath bit NOT NULL DEFAULT 0,
	MaterialSourceLSID LSIDType NOT NULL,

	-- Captions for extra per-mouse columns... inelegant, but simple
	int1Caption nvarchar(63) NULL,
	int2Caption nvarchar(63) NULL,
	int3Caption nvarchar(63) NULL,
	date1Caption nvarchar(63) NULL,
	date2Caption nvarchar(63) NULL,
	string1Caption nvarchar(63) NULL,
	string2Caption nvarchar(63) NULL,
	string3Caption nvarchar(63) NULL,

    CONSTRAINT PK_MouseModel PRIMARY KEY (modelId),
	CONSTRAINT UQ_Model_Name UNIQUE (container, name)
    )
    GO


CREATE TABLE mousemod.BreedingPair (
    breedingPairId INT IDENTITY (1,1),
    modelId INT NOT NULL ,
    pairName nvarchar(100) NOT NULL,
    container ENTITYID,
    entityId ENTITYID,
    dateJoined DATETIME,
    maleNotes nvarchar(255),
    femaleNotes nvarchar(255),

    CONSTRAINT PK_BreedingPair PRIMARY KEY (breedingPairId),
    CONSTRAINT FK_BreedingPair_ModelId FOREIGN KEY (modelId) REFERENCES mousemod.MouseModel(modelId)
    )
    GO

CREATE TABLE mousemod.Litter (
   litterId INT IDENTITY (1,1) ,
   breedingPairId INT NOT NULL,
   name NVARCHAR(40),
   container ENTITYID,
   birthDate DATETIME,
   males INT NOT NULL,
   females INT NOT NULL,

   CONSTRAINT PK_Litter PRIMARY KEY(litterId),
   CONSTRAINT FK_Litter_BreedingPairId  FOREIGN KEY(breedingPairId) REFERENCES mousemod.BreedingPair(breedingPairId)
   )
   GO

CREATE TABLE mousemod.Cage (
   cageId INT IDENTITY (1,1),
   cageName nvarchar(2) NOT NULL,
   container ENTITYID,
   modelId INT NOT NULL,
   sex nvarchar(1) NOT NULL,
   necropsyComplete bit NOT NULL DEFAULT 0,
   bleedOutComplete bit NOT NULL DEFAULT 0,
   CONSTRAINT PK_Cage PRIMARY KEY (cageId),
   CONSTRAINT FK_Cage_ModelId FOREIGN KEY (modelId) REFERENCES mousemod.MouseModel(modelId)
   )
   GO

CREATE TABLE mousemod.Mouse (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,

    mouseId INT IDENTITY (1,1), -- Just used to enable reselect
	Container ENTITYID,			--container/path
    	EntityId ENTITYID ,
	MouseNo nvarchar (128) NOT NULL,
    toeNo int NOT NULL,
    modelId int NOT NULL,
    cageId int NOT NULL,
    litterId INT,
	Sex nvarchar (1)  NULL ,
    control bit NOT NULL DEFAULT 0,
	BirthDate datetime NULL ,
	DeathDate datetime NULL ,
	StartDate datetime NULL , -- Date started in experiment
    TreatmentDate datetime NULL,
	MouseComments nvarchar (100)  NULL ,
    necropsyComplete bit NOT NULL DEFAULT 0,
    bleedOutComplete bit NOT NULL DEFAULT 0,
    necropsyAppearance ntext NULL,
    necropsyGrossFindings ntext NULL,

    int1 INT NULL,
    int2 INT NULL,
    int3 INT NULL,
    date1 datetime NULL,
    date2 datetime NULL,
    string1 nvarchar(100) NULL,
    string2 nvarchar(100) NULL,
    string3 nvarchar(100) NULL,
    CONSTRAINT PK_Mouse PRIMARY KEY (EntityId),
	CONSTRAINT UQ_Mouse_No UNIQUE (Container, MouseNo),
	CONSTRAINT FK_Mouse_CageId FOREIGN KEY (cageId) REFERENCES mousemod.Cage(cageId),
	CONSTRAINT FK_Mouse_LitterId FOREIGN KEY (litterId) REFERENCES mousemod.Litter(litterId),
	CONSTRAINT FK_Mouse_MouseModel FOREIGN KEY (ModelId) REFERENCES mousemod.MouseModel(modelId)
)
GO

CREATE VIEW mousemod.MouseView AS
    Select mouseId, mousemod.Mouse.modelId modelId, mousemod.Mouse.Container Container, EntityId, MouseNo, mousemod.Cage.CageName As CageName, mousemod.Mouse.Sex Sex,
    mousemod.Mouse.Control Control, BirthDate, StartDate, DeathDate,MouseComments,toeNo,
    int1, int2, int3, date1, date2, string1, string2, string3,
    CASE WHEN DeathDate IS NULL THEN
        CASE WHEN StartDate IS NULL THEN
            DATEDIFF(week, BirthDate, GETDATE())
         ELSE
            DATEDIFF(week, StartDate, GETDATE())
         END
    ELSE
        CASE WHEN StartDate IS NULL THEN
            DATEDIFF(week, BirthDate, DeathDate)
         ELSE
            DATEDIFF(week, StartDate, DeathDate)
         END
    END
         Weeks From mousemod.Mouse JOIN mousemod.Cage on mousemod.Mouse.CageId = mousemod.Cage.CageId
GO

CREATE TABLE mousemod.MouseStrain (
	MouseStrainId int IDENTITY (1, 1),
	MouseStrain nvarchar (50)  NULL ,
	Characteristics nvarchar (50)  NULL,
	container ENTITYID,			--lookupContainer
	CONSTRAINT PK_MouseStrain PRIMARY KEY (mouseStrainId)
)
GO

CREATE TABLE mousemod.TargetGene (
	TargetGeneId int IDENTITY (1, 1),
	TargetGene nvarchar (50)  NULL ,
	TG_Characteristic nvarchar (50)  NULL ,
	DateAdded datetime NULL,
	container ENTITYID,			--lookupContainer
	CONSTRAINT PK_TargetGene PRIMARY KEY (TargetGeneId)
)
GO

CREATE TABLE mousemod.SamplePreparation (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,
    PreparationId int IDENTITY (1, 1),
    Name nvarchar(100) NOT NULL,
    Description nvarchar(100) NULL,
	container ENTITYID,			--lookupContainer
	CONSTRAINT PK_SamplePreparation PRIMARY KEY (PreparationId)
)
GO


CREATE TABLE mousemod.Sample (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,

	Container ENTITYID,			--container/path
    OrganismId ENTITYID,
    LSID LSIDtype NOT NULL,
    SampleId nvarchar (100) NOT NULL, -- what is their id for it
	SampleTypeId int NULL ,
    EntityId ENTITYID UNIQUE, -- used for joining attachments etc
    CollectionDate DATETIME NOT NULL,
	Description NTEXT  NULL,
    Fixed bit NOT NULL DEFAULT 0,
    Frozen bit NOT NULL DEFAULT 1,
    FrozenUsed bit NOT NULL DEFAULT 0,
	CONSTRAINT PK_Sample PRIMARY KEY (LSID)
)
GO


CREATE TABLE mousemod.SampleType (
	SampleTypeId int IDENTITY (1, 1),
	SampleType nvarchar (30)  NULL ,
	Created DATETIME NULL,
	Lymphoid bit NOT NULL DEFAULT 0 ,
	SelectTiss int NULL,
	container ENTITYID,			--lookupContainer
	CONSTRAINT PK_SampleType PRIMARY KEY(SampleTypeId)
)
GO

CREATE TABLE mousemod.Treatment (
	TreatmentId int IDENTITY (1, 1),
	Treatment nvarchar (25)  NULL ,
    container ENTITYID,			--lookupContainer
    CONSTRAINT PK_Treatment PRIMARY KEY (TreatmentId)

)
GO

CREATE TABLE mousemod.Stain (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    StainId int IDENTITY (1, 1),
    Name nvarchar(25),
    Container ENTITYID,
    CONSTRAINT PK_Stain PRIMARY KEY(StainId)
)
GO

CREATE TABLE mousemod.Slide (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    RowId int IDENTITY(1,1),
    EntityId ENTITYID, -- Used for attachments
    SampleLSID LSIDtype NOT NULL,
    StainId int ,
    Notes ntext,
    Container ENTITYID,

    CONSTRAINT PK_Slide PRIMARY KEY(RowId),
    CONSTRAINT FK_Slide_SampleLSID FOREIGN KEY(SampleLSID) REFERENCES mousemod.Sample(LSID),
    CONSTRAINT FK_Slide_StainId FOREIGN KEY(StainId) REFERENCES mousemod.Stain(StainId)
)
GO


CREATE TABLE mousemod.TaskType (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    TaskTypeId int IDENTITY(1,1) ,
    Name nvarchar(25),
    Container ENTITYID, --Lookup container
    CONSTRAINT PK_TaskType PRIMARY KEY(TaskTypeId)
)
GO

CREATE TABLE mousemod.MouseTask (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    TaskId int IDENTITY(1,1),
    Complete bit NOT NULL DEFAULT 0,
    MouseEntityId ENTITYID ,
    ModelId int NOT NULL,
    TaskTypeId int ,
    TaskDate DATETIME, -- Date the necropsy (etc) occurs in real life (not date entered)
    Container ENTITYID,

    CONSTRAINT PK_MouseTask PRIMARY KEY(TaskId),
    CONSTRAINT FK_MouseTask_MouseEntityId FOREIGN KEY(MouseEntityId) REFERENCES mousemod.Mouse(EntityId),
    CONSTRAINT FK_MouseTask_TaskTypeId FOREIGN KEY(TaskTypeId) REFERENCES mousemod.TaskType(TaskTypeId)
)
GO

-- INVENTORY TABLES


CREATE TABLE mousemod.Location (
    SampleLSID LSIDType NOT NULL,
    Freezer nvarchar(50) NULL,
    Rack nvarchar(50) NULL,
    Shelf nvarchar(50) NULL,
    Drawer nvarchar(50) NULL,
    Box nvarchar(50) NOT NULL,
    Cell int NOT NULL,
    CONSTRAINT PK_Location PRIMARY KEY (SampleLSID),
    CONSTRAINT UQ_Location_One_Per_Slot UNIQUE (Freezer,Rack,Shelf,Drawer,Box,Cell),
)
GO

ALTER TABLE mousemod.Mouse ADD
	CONSTRAINT CK_Mouse_Sex CHECK (Sex = 'M' or Sex = 'F' or Sex = 'U' or Sex is null)
GO

 CREATE  INDEX GenotypeId ON mousemod.MouseModel(GenotypeId)
GO

 CREATE  INDEX IrradDoseId ON mousemod.MouseModel(IrradDoseId)
GO


 CREATE  INDEX TreatmentId ON mousemod.MouseModel(TreatmentId)
GO

 CREATE  INDEX MouseNo ON mousemod.Mouse(MouseNo)
GO

ALTER TABLE mousemod.MouseModel ADD
	CONSTRAINT FK_MouseModel_TargetGeneId FOREIGN KEY
	(
		TargetGeneId
	) REFERENCES mousemod.TargetGene (
		TargetGeneId
	),
	CONSTRAINT FK_MouseModel_MouseStrainId FOREIGN KEY
	(
		MouseStrainId
	) REFERENCES mousemod.MouseStrain (
		MouseStrainId
	),
	CONSTRAINT FK_MouseModel_TreatmentId FOREIGN KEY
	(
		TreatmentId
	) REFERENCES mousemod.Treatment (
		TreatmentId
	),
	CONSTRAINT FK_MouseModel_IrradDoseId FOREIGN KEY
	(
		IrradDoseId
	) REFERENCES mousemod.IrradDose (
		IrradDoseId
	),
	CONSTRAINT FK_MouseModel_GenotypeId FOREIGN KEY
	(
		GenotypeId
	) REFERENCES mousemod.Genotype (
		GenotypeId
	)
GO

ALTER TABLE mousemod.Sample ADD
	CONSTRAINT FK_Sample_SampleTypeId FOREIGN KEY
	(
		SampleTypeId
	) REFERENCES mousemod.SampleType (
		SampleTypeId
	),
	CONSTRAINT FK_Sample_OrganismId FOREIGN KEY
	(
		OrganismId
	) REFERENCES mousemod.Mouse (
		EntityId
	) ON UPDATE CASCADE
GO

ALTER TABLE mousemod.Cage ADD CONSTRAINT UQ_CageName UNIQUE (container,modelId,cageName)
GO

-- Create Views
CREATE VIEW mousemod.MouseSample AS
SELECT
    mousemod.Sample.SampleId SampleId, mousemod.Sample.LSID LSID, mousemod.SampleType.SampleType SampleType, mousemod.Sample.EntityId sampleEntityId,
    mousemod.Sample.CollectionDate CollectionDate, mousemod.Sample.Description Description, mousemod.Sample.Fixed Fixed, mousemod.Sample.Frozen Frozen, mousemod.Sample.FrozenUsed FrozenUsed,
    mousemod.Mouse.MouseNo MouseNo, mousemod.Mouse.Control Control, mousemod.Mouse.BirthDate BirthDate, mousemod.Mouse.DeathDate DeathDate, mousemod.Mouse.Sex Sex, mousemod.Mouse.ModelId ModelId,
    mousemod.Mouse.EntityId mouseEntityId, mousemod.Mouse.Container Container, mousemod.Location.Freezer Freezer, mousemod.Location.Box Box, mousemod.Location.Rack Rack, mousemod.Location.Cell Cell, DATEDIFF(week, mousemod.Mouse.BirthDate, mousemod.Sample.CollectionDate) Weeks
FROM mousemod.Sample
	JOIN mousemod.SampleType ON mousemod.Sample.SampleTypeId = mousemod.SampleType.SampleTypeId
	JOIN mousemod.Mouse ON mousemod.Sample.OrganismId = mousemod.Mouse.EntityId
    LEFT OUTER JOIN mousemod.Location ON mousemod.Sample.LSID = mousemod.Location.SampleLSID
GO

CREATE VIEW mousemod.MouseSlide AS
SELECT mousemod.SampleType.SampleType AS SampleType,mousemod.Stain.Name AS Stain, mousemod.Mouse.mouseNo AS mouseNo, mousemod.Mouse.modelId AS modelId,
       mousemod.Slide.entityId AS slideEntityId, mousemod.Sample.organismid AS mouseEntityId, mousemod.sample.sampleId AS sampleId,
       mousemod.sample.LSId AS LSID,DocumentName, mousemod.slide.notes AS Notes, mousemod.mouse.Container as Container
    FROM mousemod.slide
	JOIN core.documents ON mousemod.slide.entityId = core.documents.parent
	JOIN mousemod.Sample ON mousemod.Slide.SampleLSID = mousemod.Sample.LSID
	JOIN mousemod.Mouse ON mousemod.Sample.organismId = mousemod.Mouse.entityId
	JOIN mousemod.SampleType ON mousemod.Sample.sampleTypeId= mousemod.SampleType.SampleTypeId
	JOIN mousemod.Stain ON mousemod.Slide.StainId = mousemod.Stain.StainId
GO

CREATE PROCEDURE mousemod.populateLookups(@lookupContainer ENTITYID) AS
    BEGIN
SET IDENTITY_INSERT mousemod.Genotype ON
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (1, '-/-', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (2, '+/+', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (3, '+/-', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (4, '+/+ +/-', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (5, '+/+ +/+', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (6, '+/+ -/-', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (7, '+/- +/+', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (8, '+/- +/-', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (9, '+/- -/-', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (10, '-/- +/+', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (11, '-/- +/-', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (12, '-/- -/-', @lookupContainer)
INSERT INTO mousemod.Genotype (GenotypeId, Genotype, Container) VALUES (14, 'UNK', @lookupContainer)
SET IDENTITY_INSERT mousemod.Genotype OFF

SET IDENTITY_INSERT mousemod.IrradDose ON
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (1, 'None', 100, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (2, '0.2Gy/24hr', 2, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (3, '0.2Gy/4hr', 1, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (4, '0.33Gy/4hr', 4, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (5, '1Gy/24hr', 8, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (6, '1Gy/48hr', 9, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (7, '1Gy/4hr', 6, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (8, '2Gy', 15, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (9, '2Gy/2.5hr', 17, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (10, '2Gy/3hr', 18, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (11, '2Gy/4hr', 19, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (12, '2hr', 0, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (13, '4Gy', 23, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (14, '4Gy/1hr', 25, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (15, '4Gy/24hr', 31, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (16, '4Gy/2hr', 26, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (17, '4Gy/36hr', 33, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (18, '4Gy/48hr', 35, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (19, '4Gy/4hr', 27, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (20, '4Gy/72hr', 37, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (21, '8Gy/24hr', 54, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (22, '8Gy/48hr', 56, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (23, '2Gy/2hr', 16, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (24, 'Unknown', 500, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (25, '4Gy/6hr', 28, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (26, '0.33Gy/3hr', 3, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (27, '8Gy/4hr', 52, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (28, 'etop/24hr', 94, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (29, 'etop/48hr', 96, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (30, 'etop/4hr', 90, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (31, '8Gy/72hr', 58, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (32, 'etop/12hr', 92, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (33, '4Gy/10hr', 29, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (34, '10GY/10HR', 70, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (35, '1Gy/12hr', 7, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (36, '8Gy/4d', 60, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (37, '1Gy/2hr', 5, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (38, '16Gy/1hr', 80, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (39, '8Gy', 50, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (40, '8Gy/2weeks', 62, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (41, '8Gy/2hr', 0, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (42, '0.6Gy/2hr', 0, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (43, '3Gy/2hr', 0, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (44, '0.6Gy/2weeks', 0, @lookupContainer)
INSERT INTO mousemod.IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (45, '0.6Gy/6weeks', 0, @lookupContainer)
SET IDENTITY_INSERT mousemod.IrradDose OFF


SET IDENTITY_INSERT mousemod.MouseStrain ON
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (1, 'NOT SPECIFIED', 'not specified', @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (2, '129', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (3, 'A/J', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (4, 'B10', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (5, 'B6', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (6, 'Balb/c/B6', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (7, 'C3H', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (8, 'C3H/129', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (9, 'C3H/B6', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (10, 'Balb/c/C3H', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (11, 'C3H/C57', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (12, 'NIH', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (13, 'NIH/129', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (14, 'NIH/B6', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (15, 'Spretus', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (16, '129/B6', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (17, 'Unknown', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (18, 'Balb/c', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (19, 'NIH/129/B6', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (20, 'NIH/B6/C3H', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (21, 'cast', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (22, 'Balb/c129', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (23, 'Swiss Webster', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (24, 'FVB', NULL, @lookupContainer)
INSERT INTO mousemod.MouseStrain(MouseStrainId, MouseStrain, Characteristics, Container) VALUES (25, 'C57BL/6', NULL, @lookupContainer)
SET IDENTITY_INSERT mousemod.MouseStrain OFF

SET IDENTITY_INSERT mousemod.TargetGene ON
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (1, 'NOT SPECIFIED', 'NoTargetGene specified', NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (2, 'abl', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (3, 'ablp53', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (4, 'ATM', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (5, 'min', 'APC, GI tumors', NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (7, 'p21', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (8, 'p27', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (9, 'p27min', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (10, 'p27p53', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (11, 'p53', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (12, 'RAG1', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (13, 'RAG2', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (14, 'SCID', 'Immuno-compromised', NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (15, 'SCIDATM', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (16, 'SCIDp53', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (17, 'CTCF', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (18, 'CTCFp53', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (19, 'wnt', 'breast tumors', NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (20, 'p27wnt', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (21, 'SMAD3', 'colon tumors', NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (22, 'SMAD4', 'colon tumors', NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (23, 'SMAD3p27', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (24, 'p19', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (25, 'ATR-MU', 'Lck promoter, thymic tumors', NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (26, 'ATR-WT', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (27, 'SCIDATR-MU', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (28, 'SCIDmin', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (29, 'wt', 'wild type - No Target Gene', NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (30, 'DNA-PK', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (31, 'NFI', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (32, 'APC1638N', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (33, 'ATR-MUp53', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (34, 'ATR-WTp53', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (35, 'WST', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (36, 'SRC', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (37, 'FYN', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (38, 'p19p53', NULL, NULL, @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (39, 'SMAD4p27', NULL, '6/22/01 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (40, 'GAPNF1', NULL, '6/22/01 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (42, 'YES', NULL, '7/2/01 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (43, 'Unk', 'Unknown', '7/3/01 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (44, 'nude', NULL, '7/24/02 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (45, 'ABLSCID', NULL, '8/6/02 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (46, 'ATMp53', NULL, '9/9/02 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (47, 'MAD', NULL, '10/17/02 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (48, 'CTCFp27', NULL, '11/14/02 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (49, 'ATRNTp53', NULL, '11/24/02 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (50, 'DNA-PKp53', NULL, '5/21/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (51, 'KU', NULL, '5/23/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (52, 'KUp53', NULL, '5/23/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (53, 'SCIDp53min', NULL, '8/20/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (54, 'Kras1', NULL, '10/1/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (55, 'p19min', NULL, '10/1/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (56, 'p27APC1638N', NULL, '10/15/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (57, 'p19APC1638N', NULL, '11/19/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (58, 'Neu', NULL, '11/19/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (59, 'SV40Tag (Rb and p53)', NULL, '11/19/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (60, 'cMyc', NULL, '11/19/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (61, 'Pten', NULL, '11/19/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (62, 'ApcMin/Apc1638', NULL, '11/19/03 12:00 AM', @lookupContainer)
INSERT INTO mousemod.TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (63, 'Hras', NULL, '11/19/03 12:00 AM', @lookupContainer)
SET IDENTITY_INSERT mousemod.TargetGene OFF

SET IDENTITY_INSERT mousemod.SampleType ON
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (1000, 'plasma', '12/8/04 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (1001, 'urine', '12/8/04 12:00 AM', '0', 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (1002, 'mammary tissue', '12/8/04 12:00 AM', '0', 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (1, 'adrenal', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (2, 'anal tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (3, 'arm tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (4, 'bladder', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (5, 'bladder tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (6, 'blood smear', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (7, 'bone marrow (BM)', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (8, 'brain', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (9, 'breast', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (10, 'breast adenocarcinoma', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (11, 'breast carcinoma', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (12, 'CA', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (13, 'cervical', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (14, 'colon', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (15, 'colon tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (16, 'duodenal tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (17, 'duodenum', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (18, 'ear', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (19, 'embryo', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (20, 'esophagus', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (21, 'eye', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (22, 'face tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (23, 'fatty Sample', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (24, 'fibrosarcoma', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (25, 'GI', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (26, 'GI tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (27, 'head tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (28, 'heart', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (29, 'heamangioma', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (30, 'kidney', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (31, 'liver', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (32, 'liver tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (37, 'lung', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (38, 'mammary tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (39, 'marrow', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (40, 'mesentary tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (41, 'Mixed', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (42, 'other', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (43, 'ovaries', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (44, 'PA', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (45, 'PA/CA', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (46, 'pancreas', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (47, 'peyers', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (48, 'salivary gland', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (49, 'sarcoma', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (50, 'shoulder tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (51, 'skin', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (52, 'small intestine tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (54, 'stomach', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (55, 'stomach tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (56, 'tail', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (57, 'testes', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (58, 'thymoma', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (60, 'tongue', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (61, 'uterus', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (62, 'breast tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (63, 'eye tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (64, 'lung tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (65, 'normal Sample', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (66, 'ovarian tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (67, 'pituitary', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (68, 'pituitary tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (69, 'preputial gland', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (70, 'red clot', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (71, 'testicular cyst', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (72, 'uterine tumor', NULL, 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (74, 'LN tumor', '4/18/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (75, 'vagina', '4/18/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (76, 'tumor', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (77, 'muscle', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (78, 'red cysts', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (79, 'seminal vesicle', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (80, 'blood vessel tumor', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (81, 'leg tumor', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (82, 'pancreatic tumor', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (83, 'rectum', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (84, 'fat tumor', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (85, 'prostate', '4/20/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (87, 'lymphoid', '6/22/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (88, 'intestinal nodules', '7/2/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (89, 'nodule, unknown', '7/2/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (90, 'spleen', '10/30/01 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (91, 'gall bladder', '11/1/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (92, 'harderian gland', '11/1/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (93, 'ureter', '11/1/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (94, 'LN', '11/1/01 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (96, 'LN-brachial', '11/6/01 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (97, 'LN-cervical', '11/6/01 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (98, 'LN-hepatic', '11/6/01 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (99, 'LN-inguinal', '11/6/01 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (100, 'LN-mesenteric', '11/6/01 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (101, 'thymus', '11/6/01 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (102, 'facial gland', '11/26/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (103, 'adrenal tumor', '11/28/01 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (104, 'LN', '1/8/02 12:00 AM', 1, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (105, 'leg', '1/8/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (106, 'uterus with ovaries', '1/8/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (107, 'unknown anomaly', '1/8/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (108, 'thymoma', '1/9/02 12:00 AM', 0, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (109, 'kidney tumor', '6/13/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (110, 'scabby skin', '6/18/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (111, 'spleen tumor', '6/25/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (112, 'LN-peritoneal', '6/26/02 12:00 AM', 1, 1, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (113, 'cervix with tumor', '6/26/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (114, 'cervix', '6/27/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (115, 'cecum', '7/17/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (116, 'lacrimal gland', '10/3/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (117, 'thyroid tumor', '10/9/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (118, 'thyroid', '10/9/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (119, 'bone', '10/9/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (120, 'cecal tumor', '10/29/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (121, 'diaphram', '11/5/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (122, 'brain tumor', '11/5/02 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (123, 'osteosarcoma', '6/25/03 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (124, 'testicular tumor', '8/18/03 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (125, 'GI-proximal', '10/1/03 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (126, 'GI-medial', '10/1/03 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (127, 'GI-distal', '10/1/03 12:00 AM', 0, 0, @lookupContainer)
INSERT INTO mousemod.SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (128, 'ovarian cyst', '10/27/03 12:00 AM', 0, 0, @lookupContainer)
SET IDENTITY_INSERT mousemod.SampleType OFF

SET IDENTITY_INSERT mousemod.Treatment ON
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (1, 'No Treatment', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (2, 'Control', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (3, '4Gy', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (4, 'DEN', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (5, 'DMBA', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (6, 'DMBA/TPA', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (7, 'ENU', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (8, 'ENU/DMBA/TPA', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (9, 'TPA', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (10, 'Urethane', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (12, 'TPA/24hrs', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (13, 'TPA/48hrs', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (14, 'TPA/72hrs', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (15, 'Partial Hep.', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (16, 'DMH', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (17, 'Dox/4hr', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (18, 'transplanted tumor', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (19, 'Dex/4hrs', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (21, 'BrDU 1hr', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (22, 'Anti TNF', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (23, '5FU 40mg', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (24, 'DMBA/TPA/GELD', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (25, 'Breeder', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (26, '1Gy at birth', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (27, 'Etoposide/4hr', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (28, 'estrogen/progesterone pel', @lookupContainer)
INSERT INTO mousemod.Treatment (TreatmentId, Treatment, Container) VALUES (29, 'ovarectomy', @lookupContainer)
SET IDENTITY_INSERT mousemod.Treatment OFF

SET IDENTITY_INSERT mousemod.SamplePreparation ON
INSERT INTO mousemod.SamplePreparation (PreparationId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (1, 1001, GETDATE(), 1001, GETDATE(), 'Frozen', @lookupContainer)
INSERT INTO mousemod.SamplePreparation (PreparationId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (2, 1001, GETDATE(), 1001, GETDATE(), 'Fixed', @lookupContainer)
SET IDENTITY_INSERT mousemod.SamplePreparation OFF

SET IDENTITY_INSERT mousemod.Stain ON
INSERT INTO mousemod.Stain (StainId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (1, 1001, GETDATE(), 1001, GETDATE(), 'H & E', @lookupContainer)
SET IDENTITY_INSERT mousemod.Stain OFF

SET IDENTITY_INSERT mousemod.TaskType ON
INSERT INTO mousemod.TaskType (TaskTypeId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (1, 1001, GETDATE(), 1001, GETDATE(), 'Necropsy', @lookupContainer)
INSERT INTO mousemod.TaskType (TaskTypeId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (2, 1001, GETDATE(), 1001, GETDATE(), 'Final Bleed', @lookupContainer)
INSERT INTO mousemod.TaskType (TaskTypeId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (3, 1001, GETDATE(), 1001, GETDATE(), 'Serial Bleed', @lookupContainer)
SET IDENTITY_INSERT mousemod.TaskType OFF

    END

GO
