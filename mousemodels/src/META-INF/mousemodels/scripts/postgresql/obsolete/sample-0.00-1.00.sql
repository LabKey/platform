/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
-- Create "sample" tables, views, etc.  Create the "sample" database first, then run this script.

/*
   NOTE: ONE SIGNIFICANT UNDONE HERE.
   1) MouseSlide VIEW (and corresponding code) needs to be changed to elimninate cross-database join
*/

ALTER DATABASE cpas SET default_with_oids TO OFF;
SET default_with_oids TO OFF;

CREATE DOMAIN ENTITYID AS VARCHAR(36);
CREATE DOMAIN USERID AS INT;

CREATE TABLE Genotype (
	GenotypeId SERIAL PRIMARY KEY,
	Genotype varchar (7)  NULL, 
	container ENTITYID			--lookupContainer
    );

CREATE TABLE IrradDose (
	IrradDoseId SERIAL PRIMARY KEY ,
	IrradDose varchar (30)  NULL ,
	SortOrder int NULL DEFAULT 0,
	container ENTITYID			--lookupContainer
);

CREATE TABLE MouseModel (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created TIMESTAMP,
	ModifiedBy USERID,
	Modified TIMESTAMP,

    modelId SERIAL PRIMARY KEY, 
    entityId ENTITYID NOT NULL,
    container ENTITYID,			--container/path
    name varchar(63) NOT NULL,
    emiceId varchar (127)  NULL,
    tumorType varchar(63) NOT NULL,
    targetGeneId int NULL,
    mouseStrainId int NULL,
    metastasis boolean NOT NULL DEFAULT '0',
    penetrance varchar(30) NOT NULL,
    latency varchar(30) NOT NULL,
    location varchar(30) NOT NULL,
    investigator varchar(127) NOT NULL,
	GenotypeId int NULL ,
	IrradDoseId int NULL ,
	TreatmentId int NULL ,
	PreNecroChem varchar (30)  NULL ,
	BrduAtDeath boolean NOT NULL DEFAULT '0',

	-- Captions for extra per-mouse columns... inelegant, but simple
	int1Caption varchar(63) NULL,
	int2Caption varchar(63) NULL,
	int3Caption varchar(63) NULL,
	date1Caption varchar(63) NULL,
	date2Caption varchar(63) NULL,
	string1Caption varchar(63) NULL,
	string2Caption varchar(63) NULL,
	string3Caption varchar(63) NULL,

	CONSTRAINT Unique_Model_Name UNIQUE (name)
    );
    
  
CREATE TABLE BreedingPair (
    breedingPairId SERIAL PRIMARY KEY,
    modelId INT NOT NULL REFERENCES MouseModel(modelId),
    pairName varchar(100) NOT NULL,
    container ENTITYID,
    entityId ENTITYID NOT NULL,
    dateJoined TIMESTAMP,
    maleNotes varchar(255),
    femaleNotes varchar(255)
    );  
    
CREATE TABLE Litter (
   litterId SERIAL PRIMARY KEY,
   breedingPairId INT NOT NULL REFERENCES BreedingPair(breedingPairId),
   name VARCHAR(20),
   container ENTITYID,
   birthDate TIMESTAMP,
   males INT NOT NULL,
   females INT NOT NULL
   );

CREATE TABLE Cage (
   cageId SERIAL PRIMARY KEY,
   cageName varchar(2) NOT NULL,
   container ENTITYID,
   litterId INT NOT NULL REFERENCES Litter(litterId),
   modelId INT NOT NULL REFERENCES MouseModel(modelId),
   sex varchar(1) NOT NULL,
   necropsyComplete boolean NOT NULL DEFAULT '0',
   bleedOutComplete boolean NOT NULL DEFAULT '0'
   );
   
CREATE TABLE Mouse (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created TIMESTAMP,
	ModifiedBy USERID,
	Modified TIMESTAMP,

    mouseId SERIAL, -- Just used to enable reselect
	Container ENTITYID,			--container/path
    	EntityId ENTITYID PRIMARY KEY,    
	MouseNo varchar (20) NOT NULL,
    toeNo int NOT NULL,
    modelId int REFERENCES MouseModel(modelId),
    cageId int REFERENCES Cage(cageId),
	Sex varchar (1)  NULL ,
    control boolean NOT NULL DEFAULT '0',
	BirthDate DATE NULL,  -- Is DATE type precise enough for these four??  Makes diff math in view easier...
	DeathDate DATE NULL,
	StartDate DATE NULL, -- Date started in experiment
    TreatmentDate DATE NULL,
	MouseComments varchar (100)  NULL ,
    necropsyComplete boolean NOT NULL DEFAULT '0',
    bleedOutComplete boolean NOT NULL DEFAULT '0',
    necropsyAppearance TEXT NULL,
    necropsyGrossFindings TEXT NULL,

    int1 INT NULL,
    int2 INT NULL,
    int3 INT NULL,
    date1 TIMESTAMP NULL,
    date2 TIMESTAMP NULL,
    string1 varchar(100) NULL,
    string2 varchar(100) NULL,
    string3 varchar(100) NULL,
	CONSTRAINT Unique_Mouse_No UNIQUE (Container, MouseNo)
);

CREATE VIEW MouseView AS
    Select mouseId, Mouse.modelId AS modelId, Mouse.Container AS Container, EntityId, MouseNo, Cage.CageName AS CageName, Mouse.Sex AS Sex,
    Mouse.Control AS Control, BirthDate, StartDate, DeathDate,MouseComments,toeNo,
    int1, int2, int3, date1, date2, string1, string2, string3,
    CASE WHEN DeathDate IS NULL THEN
         CASE WHEN StartDate IS NULL THEN
         	(CURRENT_DATE - BirthDate) / 7
         ELSE
            	(CURRENT_DATE - StartDate) / 7
         END
    ELSE
         CASE WHEN StartDate IS NULL THEN
         	(DeathDate - BirthDate) / 7
         ELSE
            	(DeathDate - StartDate) / 7
         END
    END
    	AS Weeks From Mouse JOIN Cage ON Mouse.CageId = Cage.CageId;

CREATE TABLE MouseStrain (
	MouseStrainId SERIAL PRIMARY KEY,
	MouseStrain varchar (50)  NULL ,
	Characteristics varchar (50)  NULL,
	container ENTITYID			--lookupContainer 
);

CREATE TABLE TargetGene (
	TargetGeneId SERIAL PRIMARY KEY,
	TargetGene varchar (50)  NULL ,
	TG_Characteristic varchar (50)  NULL ,
	DateAdded TIMESTAMP NULL,
	container ENTITYID			--lookupContainer 
);

CREATE TABLE SamplePreparation (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created TIMESTAMP,
	ModifiedBy USERID,
	Modified TIMESTAMP,
    PreparationId SERIAL PRIMARY KEY,
    Name varchar(100) NOT NULL,
    Description varchar(100) NULL,
	container ENTITYID			--lookupContainer
); 


CREATE TABLE Sample (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created TIMESTAMP,
	ModifiedBy USERID,
	Modified TIMESTAMP,

	Container ENTITYID,			--container/path
    
    OrganismId ENTITYID REFERENCES Mouse(EntityId), -- What animal did this come from. UNDONE -- Joining to different organism types
    
    SourceId ENTITYID NOT NULL, -- who gave it to us
    SampleId varchar (100) NOT NULL, -- what is their id for it
	SampleTypeId int NULL ,
    EntityId ENTITYID NOT NULL,
    CollectionDate DATE NOT NULL,  -- is DATE type precise enough?
	Description TEXT  NULL,
    Fixed boolean NOT NULL DEFAULT '0',
    Frozen boolean NOT NULL DEFAULT '1',
    FrozenUsed boolean NOT NULL DEFAULT '0',
	CONSTRAINT Sample_PK PRIMARY KEY (SourceId, SampleId)
);

CREATE TABLE SampleType (
	SampleTypeId SERIAL PRIMARY KEY,
	SampleType varchar (30)  NULL ,
	Created TIMESTAMP NULL,
	Lymphoid boolean NOT NULL DEFAULT '0' ,
	SelectTiss int NULL, 
	container ENTITYID			--lookupContainer 
);

CREATE TABLE Treatment (
	TreatmentId SERIAL PRIMARY KEY,
	Treatment varchar (25)  NULL ,
	container ENTITYID			--lookupContainer 
);

CREATE TABLE Stain (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    
    StainId SERIAL PRIMARY KEY,
    Name varchar(25),
    Container ENTITYID
);

CREATE TABLE Slide (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    
    RowId SERIAL PRIMARY KEY,
    EntityId ENTITYID NOT NULL,
    SourceId ENTITYID NOT NULL, -- who gave it to us
    SampleId varchar (100) NOT NULL, -- what is their id for it
    StainId int REFERENCES Stain(StainId),
    Notes TEXT,
    Container ENTITYID
);


CREATE TABLE TaskType (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    
    TaskTypeId SERIAL PRIMARY KEY,
    Name varchar(25),
    Container ENTITYID --Lookup container
);

CREATE TABLE MouseTask (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    
    TaskId SERIAL PRIMARY KEY,
    Complete boolean NOT NULL DEFAULT '0',
    MouseEntityId ENTITYID REFERENCES Mouse(EntityId),
    ModelId int NOT NULL,
    TaskTypeId int REFERENCES TaskType(TaskTypeId),
    TaskDate TIMESTAMP, -- Date the necropsy (etc) occurs in real life (not date entered)
    Container ENTITYID
);

-- INVENTORY TABLES

CREATE TABLE Freezer (
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    
  FreezerId SERIAL PRIMARY KEY,
  Name varchar(100) NOT NULL,
  Container ENTITYID -- lookupContainer
  );

CREATE TABLE Box (
	BoxId SERIAL PRIMARY KEY,
    FreezerId int REFERENCES Freezer(FreezerId),
    Shelf varchar(4),
    Rack varchar(4),
    Drawer varchar(4),
    BoxName varchar(25) NOT NULL,
    MaxItemsInBox int DEFAULT 100,
    Container ENTITYID,
    CONSTRAINT Location_Unique UNIQUE (FreezerId, Shelf, Rack, Drawer, BoxName)
);

CREATE TABLE SampleSource (
    _ts TIMESTAMP DEFAULT now() ,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    
    SourceId ENTITYID PRIMARY KEY,

    SourceName varchar(30) NOT NULL,
    CONSTRAINT SourceName_Unique UNIQUE (SourceName),
	container ENTITYID			--lookupContainer
);

CREATE TABLE Inventory (
    SourceId ENTITYID REFERENCES SampleSource(SourceId),
    SampleId varchar(100),
    PreparationId int NOT NULL REFERENCES SamplePreparation(PreparationId),
    BoxId int REFERENCES Box(BoxId),
    Row int NULL,
    Col int NULL,
	CONSTRAINT Inventory_PK PRIMARY KEY (SourceId, SampleId, PreparationId),
          CONSTRAINT Inventory_One_Per_Slot UNIQUE (BoxId, Row,Col)
);


ALTER TABLE Mouse ADD 
	CONSTRAINT CK_Mouse_Sex CHECK (Sex = 'M' or Sex = 'F' or Sex = 'U' or Sex is null);

CREATE INDEX GenotypeId ON MouseModel(GenotypeId);

CREATE INDEX IrradDoseId ON MouseModel(IrradDoseId);

CREATE INDEX TreatmentId ON MouseModel(TreatmentId);

CREATE INDEX MouseNo ON Mouse(MouseNo);

ALTER TABLE MouseModel
	ADD CONSTRAINT MouseModel_FK00 FOREIGN KEY (TreatmentId) REFERENCES Treatment (TreatmentId),
	ADD CONSTRAINT MouseModel_FK01 FOREIGN KEY (TargetGeneId) REFERENCES TargetGene (TargetGeneId),
	ADD CONSTRAINT MouseModel_FK02 FOREIGN KEY (IrradDoseId) REFERENCES IrradDose (IrradDoseId),
	ADD CONSTRAINT MouseModel_FK03 FOREIGN KEY (MouseStrainId) REFERENCES MouseStrain (MouseStrainId),
	ADD CONSTRAINT MouseModel_FK04 FOREIGN KEY (GenotypeId) REFERENCES Genotype (GenotypeId);

ALTER TABLE Sample 
	ADD CONSTRAINT Sample_FK00 FOREIGN KEY (SampleTypeId) REFERENCES SampleType (SampleTypeId),
	ADD CONSTRAINT Sample_FK01 FOREIGN KEY (OrganismId) REFERENCES Mouse (EntityId) ON UPDATE CASCADE,
	ADD CONSTRAINT Sample_FK02 FOREIGN KEY (SourceId) REFERENCES SampleSource (SourceId);

ALTER TABLE Cage
	ADD CONSTRAINT CageNameUnique UNIQUE (container, modelId, cageName);

-- Create Views
CREATE VIEW MouseSample AS
SELECT
    Sample.SampleId AS SampleId, SampleType.SampleType AS SampleType, Sample.SourceId AS SourceId, Sample.EntityId AS sampleEntityId,
    Sample.CollectionDate AS CollectionDate, Sample.Description AS Description, Sample.Fixed AS Fixed, Sample.Frozen AS Frozen, Sample.FrozenUsed AS FrozenUsed,
    Mouse.MouseNo AS MouseNo, Mouse.Control AS Control, Mouse.BirthDate AS BirthDate, Mouse.DeathDate AS DeathDate, Mouse.Sex AS Sex, Mouse.ModelId AS ModelId,
    Mouse.EntityId AS mouseEntityId, Mouse.Container AS Container, Inventory.BoxId, (Sample.CollectionDate - Mouse.BirthDate) AS Weeks
FROM Sample 
	JOIN SampleType ON Sample.SampleTypeId = SampleType.SampleTypeId
	JOIN Mouse ON Sample.OrganismId = Mouse.EntityId
    LEFT OUTER JOIN Inventory ON Sample.SampleId = Inventory.SampleId AND Sample.SourceId = Inventory.SourceId;        

/*
CREATE VIEW MouseSlide AS
SELECT SampleType.SampleType AS SampleType,Stain.Name AS Stain,mouse.mouseNo AS mouseNo, mouse.modelId AS modelId,
       slide.entityId AS slideEntityId, sample.organismid AS mouseEntityId, sample.sampleId AS sampleId,
       sample.sourceId AS sourceId, DocumentName, slide.notes AS Notes, mouse.Container AS Container 
    FROM slide 
	JOIN Documents ON slide.entityId = Documents.parent 
	JOIN Sample ON slide.sampleId = sample.sampleId and slide.sourceId = sample.sourceId 
	JOIN Mouse ON sample.organismId = Mouse.entityId 
	JOIN SampleType ON Sample.sampleTypeId = SampleType.SampleTypeId 
	JOIN Stain ON Slide.StainId = Stain.StainId;
*/

-- Populate lookups: Put entire thing into a function that gets called at module update time
-- Note: We're forced to delimit the function body with single quotes (because of a JDBC driver issue),
-- and that forces us to escape all the single quotes below.

CREATE OR REPLACE FUNCTION populateLookups(text, text) RETURNS VOID AS '

DECLARE
	lookupContainer ALIAS FOR $1;
	sampleSourceId  ALIAS FOR $2;
BEGIN

INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (1, \'-/-\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (2, \'+/+\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (3, \'+/-\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (4, \'+/+ +/-\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (5, \'+/+ +/+\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (6, \'+/+ -/-\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (7, \'+/- +/+\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (8, \'+/- +/-\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (9, \'+/- -/-\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (10, \'-/- +/+\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (11, \'-/- +/-\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (12, \'-/- -/-\', lookupContainer);
INSERT INTO Genotype (GenotypeId, Genotype, Container) VALUES (14, \'UNK\', lookupContainer);


INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (1, \'None\', 100, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (2, \'0.2Gy/24hr\', 2, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (3, \'0.2Gy/4hr\', 1, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (4, \'0.33Gy/4hr\', 4, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (5, \'1Gy/24hr\', 8, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (6, \'1Gy/48hr\', 9, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (7, \'1Gy/4hr\', 6, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (8, \'2Gy\', 15, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (9, \'2Gy/2.5hr\', 17, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (10, \'2Gy/3hr\', 18, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (11, \'2Gy/4hr\', 19, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (12, \'2hr\', 0, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (13, \'4Gy\', 23, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (14, \'4Gy/1hr\', 25, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (15, \'4Gy/24hr\', 31, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (16, \'4Gy/2hr\', 26, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (17, \'4Gy/36hr\', 33, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (18, \'4Gy/48hr\', 35, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (19, \'4Gy/4hr\', 27, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (20, \'4Gy/72hr\', 37, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (21, \'8Gy/24hr\', 54, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (22, \'8Gy/48hr\', 56, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (23, \'2Gy/2hr\', 16, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (24, \'Unknown\', 500, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (25, \'4Gy/6hr\', 28, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (26, \'0.33Gy/3hr\', 3, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (27, \'8Gy/4hr\', 52, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (28, \'etop/24hr\', 94, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (29, \'etop/48hr\', 96, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (30, \'etop/4hr\', 90, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (31, \'8Gy/72hr\', 58, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (32, \'etop/12hr\', 92, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (33, \'4Gy/10hr\', 29, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (34, \'10GY/10HR\', 70, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (35, \'1Gy/12hr\', 7, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (36, \'8Gy/4d\', 60, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (37, \'1Gy/2hr\', 5, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (38, \'16Gy/1hr\', 80, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (39, \'8Gy\', 50, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (40, \'8Gy/2weeks\', 62, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (41, \'8Gy/2hr\', 0, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (42, \'0.6Gy/2hr\', 0, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (43, \'3Gy/2hr\', 0, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (44, \'0.6Gy/2weeks\', 0, lookupContainer);
INSERT INTO IrradDose (IrradDoseId, IrradDose, SortOrder, Container) VALUES (45, \'0.6Gy/6weeks\', 0, lookupContainer);


INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (1, \'NOT SPECIFIED\', \'not specified\', lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (2, \'129\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (3, \'A/J\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (4, \'B10\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (5, \'B6\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (6, \'Balb/c/B6\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (7, \'C3H\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (8, \'C3H/129\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (9, \'C3H/B6\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (10, \'Balb/c/C3H\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (11, \'C3H/C57\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (12, \'NIH\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (13, \'NIH/129\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (14, \'NIH/B6\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (15, \'Spretus\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (16, \'129/B6\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (17, \'Unknown\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (18, \'Balb/c\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (19, \'NIH/129/B6\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (20, \'NIH/B6/C3H\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (21, \'cast\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (22, \'Balb/c129\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (23, \'Swiss Webster\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (24, \'FVB\', NULL, lookupContainer);
INSERT INTO MouseStrain (MouseStrainId, MouseStrain, Characteristics, Container) VALUES (25, \'C57BL/6\', NULL, lookupContainer);


INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (1, \'NOT SPECIFIED\', \'NoTargetGene specified\', NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (2, \'abl\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (3, \'ablp53\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (4, \'ATM\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (5, \'min\', \'APC, GI tumors\', NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (7, \'p21\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (8, \'p27\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (9, \'p27min\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (10, \'p27p53\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (11, \'p53\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (12, \'RAG1\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (13, \'RAG2\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (14, \'SCID\', \'Immuno-compromised\', NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (15, \'SCIDATM\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (16, \'SCIDp53\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (17, \'CTCF\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (18, \'CTCFp53\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (19, \'wnt\', \'breast tumors\', NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (20, \'p27wnt\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (21, \'SMAD3\', \'colon tumors\', NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (22, \'SMAD4\', \'colon tumors\', NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (23, \'SMAD3p27\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (24, \'p19\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (25, \'ATR-MU\', \'Lck promoter, thymic tumors\', NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (26, \'ATR-WT\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (27, \'SCIDATR-MU\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (28, \'SCIDmin\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (29, \'wt\', \'wild type - No Target Gene\', NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (30, \'DNA-PK\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (31, \'NFI\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (32, \'APC1638N\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (33, \'ATR-MUp53\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (34, \'ATR-WTp53\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (35, \'WST\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (36, \'SRC\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (37, \'FYN\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (38, \'p19p53\', NULL, NULL, lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (39, \'SMAD4p27\', NULL, \'6/22/01 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (40, \'GAPNF1\', NULL, \'6/22/01 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (42, \'YES\', NULL, \'7/2/01 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (43, \'Unk\', \'Unknown\', \'7/3/01 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (44, \'nude\', NULL, \'7/24/02 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (45, \'ABLSCID\', NULL, \'8/6/02 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (46, \'ATMp53\', NULL, \'9/9/02 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (47, \'MAD\', NULL, \'10/17/02 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (48, \'CTCFp27\', NULL, \'11/14/02 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (49, \'ATRNTp53\', NULL, \'11/24/02 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (50, \'DNA-PKp53\', NULL, \'5/21/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (51, \'KU\', NULL, \'5/23/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (52, \'KUp53\', NULL, \'5/23/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (53, \'SCIDp53min\', NULL, \'8/20/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (54, \'Kras1\', NULL, \'10/1/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (55, \'p19min\', NULL, \'10/1/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (56, \'p27APC1638N\', NULL, \'10/15/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (57, \'p19APC1638N\', NULL, \'11/19/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (58, \'Neu\', NULL, \'11/19/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (59, \'SV40Tag (Rb and p53)\', NULL, \'11/19/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (60, \'cMyc\', NULL, \'11/19/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (61, \'Pten\', NULL, \'11/19/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (62, \'ApcMin/Apc1638\', NULL, \'11/19/03 12:00 AM\', lookupContainer);
INSERT INTO TargetGene (TargetGeneId, TargetGene, TG_Characteristic, DateAdded, Container) VALUES (63, \'Hras\', NULL, \'11/19/03 12:00 AM\', lookupContainer);


INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (1000, \'plasma\', \'12/8/04 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (1, \'adrenal\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (2, \'anal tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (3, \'arm tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (4, \'bladder\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (5, \'bladder tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (6, \'blood smear\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (7, \'bone marrow (BM)\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (8, \'brain\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (9, \'breast\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (10, \'breast adenocarcinoma\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (11, \'breast carcinoma\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (12, \'CA\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (13, \'cervical\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (14, \'colon\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (15, \'colon tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (16, \'duodenal tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (17, \'duodenum\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (18, \'ear\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (19, \'embryo\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (20, \'esophagus\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (21, \'eye\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (22, \'face tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (23, \'fatty Sample\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (24, \'fibrosarcoma\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (25, \'GI\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (26, \'GI tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (27, \'head tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (28, \'heart\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (29, \'heamangioma\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (30, \'kidney\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (31, \'liver\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (32, \'liver tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (37, \'lung\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (38, \'mammary tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (39, \'marrow\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (40, \'mesentary tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (41, \'Mixed\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (42, \'other\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (43, \'ovaries\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (44, \'PA\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (45, \'PA/CA\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (46, \'pancreas\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (47, \'peyers\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (48, \'salivary gland\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (49, \'sarcoma\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (50, \'shoulder tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (51, \'skin\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (52, \'small intestine tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (54, \'stomach\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (55, \'stomach tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (56, \'tail\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (57, \'testes\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (58, \'thymoma\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (60, \'tongue\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (61, \'uterus\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (62, \'breast tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (63, \'eye tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (64, \'lung tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (65, \'normal Sample\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (66, \'ovarian tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (67, \'pituitary\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (68, \'pituitary tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (69, \'preputial gland\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (70, \'red clot\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (71, \'testicular cyst\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (72, \'uterine tumor\', NULL, \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (74, \'LN tumor\', \'4/18/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (75, \'vagina\', \'4/18/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (76, \'tumor\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (77, \'muscle\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (78, \'red cysts\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (79, \'seminal vesicle\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (80, \'blood vessel tumor\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (81, \'leg tumor\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (82, \'pancreatic tumor\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (83, \'rectum\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (84, \'fat tumor\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (85, \'prostate\', \'4/20/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (87, \'lymphoid\', \'6/22/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (88, \'intestinal nodules\', \'7/2/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (89, \'nodule, unknown\', \'7/2/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (90, \'spleen\', \'10/30/01 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (91, \'gall bladder\', \'11/1/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (92, \'harderian gland\', \'11/1/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (93, \'ureter\', \'11/1/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (94, \'LN\', \'11/1/01 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (96, \'LN-brachial\', \'11/6/01 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (97, \'LN-cervical\', \'11/6/01 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (98, \'LN-hepatic\', \'11/6/01 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (99, \'LN-inguinal\', \'11/6/01 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (100, \'LN-mesenteric\', \'11/6/01 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (101, \'thymus\', \'11/6/01 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (102, \'facial gland\', \'11/26/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (103, \'adrenal tumor\', \'11/28/01 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (104, \'LN\', \'1/8/02 12:00 AM\', \'1\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (105, \'leg\', \'1/8/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (106, \'uterus with ovaries\', \'1/8/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (107, \'unknown anomaly\', \'1/8/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (108, \'thymoma\', \'1/9/02 12:00 AM\', \'0\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (109, \'kidney tumor\', \'6/13/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (110, \'scabby skin\', \'6/18/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (111, \'spleen tumor\', \'6/25/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (112, \'LN-peritoneal\', \'6/26/02 12:00 AM\', \'1\', 1, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (113, \'cervix with tumor\', \'6/26/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (114, \'cervix\', \'6/27/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (115, \'cecum\', \'7/17/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (116, \'lacrimal gland\', \'10/3/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (117, \'thyroid tumor\', \'10/9/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (118, \'thyroid\', \'10/9/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (119, \'bone\', \'10/9/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (120, \'cecal tumor\', \'10/29/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (121, \'diaphram\', \'11/5/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (122, \'brain tumor\', \'11/5/02 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (123, \'osteosarcoma\', \'6/25/03 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (124, \'testicular tumor\', \'8/18/03 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (125, \'GI-proximal\', \'10/1/03 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (126, \'GI-medial\', \'10/1/03 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (127, \'GI-distal\', \'10/1/03 12:00 AM\', \'0\', 0, lookupContainer);
INSERT INTO SampleType (SampleTypeId, SampleType, Created, Lymphoid, SelectTiss, Container) VALUES (128, \'ovarian cyst\', \'10/27/03 12:00 AM\', \'0\', 0, lookupContainer);


INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (1, \'No Treatment\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (2, \'Control\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (3, \'4Gy\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (4, \'DEN\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (5, \'DMBA\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (6, \'DMBA/TPA\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (7, \'ENU\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (8, \'ENU/DMBA/TPA\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (9, \'TPA\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (10, \'Urethane\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (12, \'TPA/24hrs\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (13, \'TPA/48hrs\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (14, \'TPA/72hrs\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (15, \'Partial Hep.\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (16, \'DMH\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (17, \'Dox/4hr\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (18, \'transplanted tumor\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (19, \'Dex/4hrs\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (21, \'BrDU 1hr\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (22, \'Anti TNF\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (23, \'5FU 40mg\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (24, \'DMBA/TPA/GELD\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (25, \'Breeder\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (26, \'1Gy at birth\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (27, \'Etoposide/4hr\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (28, \'estrogen/progesterone pel\', lookupContainer);
INSERT INTO Treatment (TreatmentId, Treatment, Container) VALUES (29, \'ovarectomy\', lookupContainer);


INSERT INTO SampleSource (SourceId, SourceName, CreatedBy, Created, ModifiedBy, Modified, Container) VALUES (sampleSourceId, \'EDI Mouse Models\', 1001, NOW(), 1001, NOW(), lookupContainer);


INSERT INTO SamplePreparation (PreparationId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (1, 1001, NOW(), 1001, NOW(), \'Frozen\', lookupContainer);
INSERT INTO SamplePreparation (PreparationId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (2, 1001, NOW(), 1001, NOW(), \'Fixed\', lookupContainer);


INSERT INTO Stain (StainId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (1, 1001, NOW(), 1001, NOW(), \'H & E\', lookupContainer);


INSERT INTO TaskType (TaskTypeId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (1, 1001, NOW(), 1001, NOW(), \'Necropsy\', lookupContainer);
INSERT INTO TaskType (TaskTypeId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (2, 1001, NOW(), 1001, NOW(), \'Final Bleed\', lookupContainer);
INSERT INTO TaskType (TaskTypeId, CreatedBy, Created, ModifiedBy, Modified, Name, Container) VALUES (3, 1001, NOW(), 1001, NOW(), \'Serial Bleed\', lookupContainer);

RETURN;
END;
' LANGUAGE plpgsql;
