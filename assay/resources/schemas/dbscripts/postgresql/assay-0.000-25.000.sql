/*
    For LabKey 19.2 and earlier, the assayresult schema and the Plate, WellGroup, and Well tables were managed by the
    study module. As of 19.3, the assay module now manages these objects, with the tables moving from the "study" schema
    to the new "assay" schema.
 */

CREATE SCHEMA assay;
CREATE SCHEMA assayresult;
-- Provisioned schema used by PlateMetadataDomainKind
CREATE SCHEMA assaywell;

CREATE TABLE assay.Plate
(
    RowId SERIAL,
    LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    Template BOOLEAN NOT NULL,
    DataFileId ENTITYID,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    Type VARCHAR(200),

    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
);

CREATE INDEX IX_Plate_Container ON assay.Plate(Container);

ALTER TABLE assay.plate
    ADD Modified TIMESTAMP,
    ADD ModifiedBy USERID;

ALTER TABLE assay.plate
    ALTER Modified SET NOT NULL,
    ALTER ModifiedBy SET NOT NULL;

ALTER TABLE assay.plate
   ADD CONSTRAINT uq_plate_lsid UNIQUE (lsid);

-- plate template (not instances) names are unique in each container
CREATE UNIQUE INDEX uq_plate_container_name_template ON assay.plate (container, name) WHERE template=true;

CREATE TABLE assay.WellGroup
(
    RowId SERIAL,
    PlateId INT NOT NULL,
    LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NULL,
    Template BOOLEAN NOT NULL,
    TypeName VARCHAR(50) NOT NULL,

    CONSTRAINT PK_WellGroup PRIMARY KEY (RowId),
    CONSTRAINT FK_WellGroup_Plate FOREIGN KEY (PlateId) REFERENCES assay.Plate(RowId)
);

CREATE INDEX IX_WellGroup_PlateId ON assay.WellGroup(PlateId);
CREATE INDEX IX_WellGroup_Container ON assay.WellGroup(Container);

ALTER TABLE assay.wellgroup
    ADD CONSTRAINT uq_wellgroup_lsid UNIQUE (lsid);

-- well group names must be unique within each well group type
ALTER TABLE assay.wellgroup
    ADD CONSTRAINT uq_wellgroup_plateid_typename_name UNIQUE (plateid, typename, name);

CREATE TABLE assay.Well
(
    RowId SERIAL,
    LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Value FLOAT NULL,
    Dilution FLOAT NULL,
    PlateId INT NOT NULL,
    Row INT NOT NULL,
    Col INT NOT NULL,

    CONSTRAINT PK_Well PRIMARY KEY (RowId),
    CONSTRAINT FK_Well_Plate FOREIGN KEY (PlateId) REFERENCES assay.Plate(RowId)
);

CREATE INDEX IX_Well_PlateId ON assay.Well(PlateId);
CREATE INDEX IX_Well_Container ON assay.Well(Container);

ALTER TABLE assay.well
    ADD CONSTRAINT uq_well_lsid UNIQUE (lsid);

-- each well position is unique on the plate
ALTER TABLE assay.well
    ADD CONSTRAINT uq_well_plateid_row_col UNIQUE (plateid, row, col);

ALTER TABLE Assay.Well ADD COLUMN SampleId INTEGER NULL;
ALTER TABLE Assay.Well ADD CONSTRAINT FK_SampleId_ExpMaterial FOREIGN KEY (SampleId) REFERENCES exp.material (RowId);

CREATE TABLE assay.WellGroupPositions
(
    RowId SERIAL,
    WellId INT NOT NULL,
    WellGroupId INT NOT NULL,

    CONSTRAINT PK_WellGroupPositions PRIMARY KEY (RowId),
    CONSTRAINT FK_WellGroupPositions_Well FOREIGN KEY (WellId) REFERENCES assay.Well(RowId),
    CONSTRAINT FK_WellGroupPositions_WellGroup FOREIGN KEY (WellGroupId) REFERENCES assay.WellGroup(RowId),
    CONSTRAINT UQ_WellGroupPositions_WellGroup_Well UNIQUE (WellGroupId, WellId)
);

CREATE TABLE assay.PlateProperty
(
    RowId SERIAL,
    PlateId INT NOT NULL,
    PropertyId INT NOT NULL,
    PropertyURI VARCHAR(300) NOT NULL,

    CONSTRAINT PK_PlateProperty PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateProperty_PlateId_PropertyId UNIQUE (PlateId, PropertyId),
    CONSTRAINT FK_PlateProperty_PlateId FOREIGN KEY (PlateId) REFERENCES assay.Plate(RowId),
    CONSTRAINT FK_PlateProperty_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor(PropertyId)
);

/* 24.xxx SQL scripts */

CREATE TABLE assay.PlateSet
(
    RowId INT NOT NULL,
    Name VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Created TIMESTAMP NOT NULL,
    CreatedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Archived BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_PlateSet PRIMARY KEY (RowId)
);

-- Insert a row into the plate set table for every plate in the system, store the plate row ID in the plate set table
-- in order to create the FK from the plate to plate set table
INSERT INTO assay.PlateSet (RowId, Name, Container, Created, CreatedBy, Modified, ModifiedBy)
    SELECT RowId, 'TempPlateSet', Container, now(), CreatedBy, now(), ModifiedBy FROM assay.Plate;

-- Add the plate set field to the plate table and populate it with the plate set row ID
ALTER TABLE assay.Plate ADD COLUMN PlateSet INTEGER;
UPDATE assay.Plate SET PlateSet = RowId;
ALTER TABLE assay.Plate ADD CONSTRAINT FK_Plate_PlateSet FOREIGN KEY (PlateSet) REFERENCES assay.PlateSet (RowId);
CREATE INDEX IX_Plate_PlateSet ON assay.Plate (PlateSet);

CREATE TABLE assay.PlateType
(
    RowId SERIAL,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    Description VARCHAR(300) NOT NULL,
    Archived BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_PlateType PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateType_Rows_Cols UNIQUE (Rows, Columns)
);

-- @SkipOnEmptySchemasBegin
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (3, 4, '12 well (3x4)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (4, 6, '24 well (4x6)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (6, 8, '48 well (6x8)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (8, 12, '96 well (8x12)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (16, 24, '384 well (16x24)');
INSERT INTO assay.PlateType (Rows, Columns, Description, Archived) VALUES (32, 48, '1536 well (32x48)', TRUE);
INSERT INTO assay.PlateType (Rows, Columns, Description, Archived) VALUES (0, 0, 'Invalid Plate Type (Plates which were created with non-valid row & column combinations)', TRUE);
-- @SkipOnEmptySchemasEnd

-- Rename type column to assayType
ALTER TABLE assay.Plate RENAME COLUMN Type TO AssayType;
-- Add plateType as a FK to assay.PlateType
ALTER TABLE assay.Plate ADD COLUMN PlateType INTEGER;
ALTER TABLE assay.Plate ADD CONSTRAINT FK_Plate_PlateType FOREIGN KEY (PlateType) REFERENCES assay.PlateType (RowId);

-- Add ID and description columns to Plate and PlateSet tables
ALTER TABLE assay.Plate ADD COLUMN PlateId VARCHAR(200);
ALTER TABLE assay.Plate ADD COLUMN Description VARCHAR(300);
ALTER TABLE assay.PlateSet ADD COLUMN PlateSetId VARCHAR(200);
ALTER TABLE assay.PlateSet ADD COLUMN Description VARCHAR(300);

-- Most existing plate sets will have a generated name, but mutated ones will get fixed up by the java upgrade script
UPDATE assay.PlateSet SET PlateSetId = Name;

UPDATE assay.Plate
SET PlateType =
        CASE
            WHEN (Rows = 3 AND Columns = 4) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 3 AND Columns = 4)
            WHEN (Rows = 4 AND Columns = 6) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 4 AND Columns = 6)
            WHEN (Rows = 6 AND Columns = 8) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 6 AND Columns = 8)
            WHEN (Rows = 8 AND Columns = 12) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 8 AND Columns = 12)
            WHEN (Rows = 16 AND Columns = 24) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 16 AND Columns = 24)
            WHEN (Rows = 32 AND Columns = 48) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 32 AND Columns = 48)
            ELSE (SELECT RowId FROM assay.PlateType WHERE Rows = 0 AND Columns = 0)
            END
WHERE PlateType IS NULL;

ALTER TABLE assay.Plate ALTER COLUMN PlateType SET NOT NULL;
ALTER TABLE assay.Plate DROP COLUMN Rows;
ALTER TABLE assay.Plate DROP COLUMN Columns;

-- finalize plate and plateSet ID columns
ALTER TABLE assay.Plate ALTER COLUMN PlateId SET NOT NULL;
ALTER TABLE assay.Plate ADD CONSTRAINT UQ_Plate_PlateId UNIQUE (PlateId);

ALTER TABLE assay.PlateSet ALTER COLUMN PlateSetId SET NOT NULL;
ALTER TABLE assay.PlateSet ADD CONSTRAINT UQ_PlateSet_PlateSetId UNIQUE (PlateSetId);

ALTER TABLE assay.PlateSet ADD COLUMN Type VARCHAR(64);
ALTER TABLE assay.PlateSet ADD COLUMN RootPlateSetId INT;
ALTER TABLE assay.PlateSet ADD COLUMN PrimaryPlateSetId INT;
ALTER TABLE assay.PlateSet ADD CONSTRAINT FK_PlateSet_RootPlateSetId FOREIGN KEY (RootPlateSetId) REFERENCES assay.PlateSet (RowId);
ALTER TABLE assay.PlateSet ADD CONSTRAINT FK_PlateSet_PrimaryPlateSetId FOREIGN KEY (PrimaryPlateSetId) REFERENCES assay.PlateSet (RowId);

-- Update all pre-existing plate sets to type "assay"
UPDATE assay.PlateSet SET type = 'assay';

ALTER TABLE assay.PlateSet ALTER COLUMN Type SET NOT NULL;

CREATE TABLE assay.PlateSetEdge
(
    FromPlateSetId INT NOT NULL,
    ToPlateSetId INT NOT NULL,
    RootPlateSetId INT NOT NULL,

    CONSTRAINT FK_PlateSet_FromPlate FOREIGN KEY (FromPlateSetId) REFERENCES assay.PlateSet (RowId),
    CONSTRAINT FK_PlateSet_ToPlate FOREIGN KEY (ToPlateSetId) REFERENCES assay.PlateSet (RowId),
    CONSTRAINT FK_PlateSet_RootPlate FOREIGN KEY (RootPlateSetId) REFERENCES assay.PlateSet (RowId),
    CONSTRAINT UQ_PlateSetEdge_FromPlate_ToPlate UNIQUE (FromPlateSetId, ToPlateSetId)
);

CREATE INDEX IX_PlateSetEdge_FromPlateSetId ON assay.PlateSetEdge (FromPlateSetId);
CREATE INDEX IX_PlateSetEdge_ToPlateSetId ON assay.PlateSetEdge (ToPlateSetId);
CREATE INDEX IX_PlateSetEdge_RootPlateSetId ON assay.PlateSetEdge (RootPlateSetId);

CREATE TABLE assay.Hit
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ProtocolId INT NOT NULL,
    ResultId INT NOT NULL,
    RunId INT NOT NULL,
    WellLsid VARCHAR(200) NOT NULL,

    CONSTRAINT PK_Hit PRIMARY KEY (RowId),
    CONSTRAINT FK_Hit_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_Protocol_ProtocolId FOREIGN KEY (ProtocolId) REFERENCES exp.Protocol (RowId),
    CONSTRAINT FK_Run_RunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT FK_Well_WellLsid FOREIGN KEY (WellLsid) REFERENCES assay.Well (Lsid),
    CONSTRAINT UQ_Hit_RunId_ResultId UNIQUE (RunId, ResultId)
);

ALTER TABLE assay.Hit ADD COLUMN PlateSetPath VARCHAR (4000);

ALTER TABLE assay.Hit ALTER COLUMN PlateSetPath SET NOT NULL;

ALTER TABLE assay.PlateSet ADD COLUMN Template BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE assay.Plate SET Template = False WHERE Template = True;

ALTER TABLE assay.Plate ADD COLUMN Archived BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE assay.Plate SET AssayType = 'Standard' WHERE AssayType IS NULL;
ALTER TABLE assay.Plate ALTER COLUMN AssayType SET NOT NULL;

-- Add index on assay.Well.SampleId to improve performance of DELETE operation on exp.Material table.
CREATE INDEX IX_Well_SampleId ON assay.Well (SampleId);

-- Add index on assay.WellGroupPositions.WellId to improve performance of DELETE operation on assay.Well table.
CREATE INDEX IX_WellGroupPositions_WellId ON assay.WellGroupPositions (WellId);

ALTER TABLE assay.plate ADD Barcode VARCHAR(255);
ALTER TABLE assay.plate ADD CONSTRAINT UQ_Barcode UNIQUE (Barcode);

UPDATE assay.plate SET Barcode = LPAD(rowid::text, 9, '0') WHERE plate.Barcode IS NULL AND plate.template IS FALSE;

ALTER TABLE assay.plate ADD CONSTRAINT check_template_true_barcode_null CHECK (NOT plate.template OR plate.Barcode IS NULL);

-- Specify plate metadata columns on the plate set rather than the individual plates
CREATE TABLE assay.PlateSetProperty
(
    RowId SERIAL,
    PlateSetId INT NOT NULL,
    PropertyId INT NOT NULL,
    PropertyURI VARCHAR(300) NOT NULL,

    CONSTRAINT PK_PlateSetProperty PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateSetProperty_PlateSetId_PropertyId UNIQUE (PlateSetId, PropertyId),
    CONSTRAINT FK_PlateSetProperty_PlateSetId FOREIGN KEY (PlateSetId) REFERENCES assay.PlateSet(RowId) ON DELETE CASCADE,
    CONSTRAINT FK_PlateSetProperty_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor(PropertyId) ON DELETE CASCADE
);

INSERT INTO assay.PlateSetProperty (PlateSetId, PropertyId, PropertyURI)
SELECT
    PL.PlateSet AS PlateSetId,
    PP.PropertyId,
    PP.PropertyURI
FROM assay.PlateProperty AS PP
INNER JOIN assay.Plate AS PL ON PP.PlateId = PL.RowId
GROUP BY PlateSetId, PropertyId, PropertyURI
ORDER BY PlateSetId, PropertyId;

DROP TABLE assay.PlateProperty;

ALTER TABLE assay.platesetproperty
    ADD COLUMN FieldKey VARCHAR(255);

ALTER TABLE assay.platesetproperty
    ADD CONSTRAINT either_identifier
        CHECK (PropertyURI IS NOT NULL OR FieldKey IS NOT NULL);

ALTER TABLE assay.platesetproperty ALTER COLUMN PropertyURI DROP NOT NULL;
ALTER TABLE assay.platesetproperty ALTER COLUMN PropertyId DROP NOT NULL;

ALTER TABLE assay.plateset ADD COLUMN LSID LSIDtype;

ALTER TABLE assay.plateset ALTER COLUMN LSID SET NOT NULL;

CREATE TABLE assay.FilterCriteria
(
    RowId SERIAL,
    PropertyId INT NOT NULL,
    ReferencePropertyId INT NOT NULL,
    DomainId INT NOT NULL,
    Operation VARCHAR(50) NOT NULL,
    Value VARCHAR(4000) NULL,

    CONSTRAINT PK_FilterCriteria PRIMARY KEY (RowId),
    CONSTRAINT FK_FilterCriteria_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId) ON DELETE CASCADE,
    CONSTRAINT FK_FilterCriteria_PropertyDescriptor_Reference FOREIGN KEY (ReferencePropertyId) REFERENCES exp.PropertyDescriptor (PropertyId) ON DELETE CASCADE,
    CONSTRAINT FK_FilterCriteria_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId) ON DELETE CASCADE
);
