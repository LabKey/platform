/*
    For LabKey 19.2 and earlier, the assayresult schema and the Plate, WellGroup, and Well tables were managed by the
    study module. As of 19.3, the assay module now manages these objects, with the tables moving from the "study" schema
    to the new "assay" schema.
 */

CREATE SCHEMA assay
GO
CREATE SCHEMA assayresult
GO

CREATE TABLE assay.Plate
(
    RowId INT IDENTITY(1,1),
    LSID NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(200) NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    Template BIT NOT NULL,
    DataFileId ENTITYID,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    Type NVARCHAR(200),

    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
);

CREATE INDEX IX_Plate_Container ON assay.Plate(Container);

ALTER TABLE assay.plate ADD
    Modified DATETIME,
    ModifiedBy USERID;

ALTER TABLE assay.plate ALTER COLUMN Modified DATETIME NOT NULL;
ALTER TABLE assay.plate ALTER COLUMN ModifiedBy USERID NOT NULL;

ALTER TABLE assay.plate
    ADD CONSTRAINT uq_plate_lsid UNIQUE (lsid);

-- plate template (not instances) names are unique in each container
CREATE UNIQUE INDEX uq_plate_container_name_template ON assay.plate (container, name) WHERE template=1;

CREATE TABLE assay.WellGroup
(
    RowId INT IDENTITY(1,1),
    PlateId INT NOT NULL,
    LSID NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(200) NULL,
    Template BIT NOT NULL,
    TypeName NVARCHAR(50) NOT NULL,

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
    RowId INT IDENTITY(1,1),
    LSID NVARCHAR(200) NOT NULL,
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

ALTER TABLE Assay.Well ADD SampleId INTEGER NULL;
ALTER TABLE Assay.Well ADD CONSTRAINT FK_SampleId_ExpMaterial FOREIGN KEY (SampleId) REFERENCES exp.material (RowId);

CREATE TABLE assay.WellGroupPositions
(
    RowId INT IDENTITY(1,1) NOT NULL,
    WellId INT NOT NULL,
    WellGroupId INT NOT NULL,

    CONSTRAINT PK_WellGroupPositions PRIMARY KEY (RowId),
    CONSTRAINT FK_WellGroupPositions_Well FOREIGN KEY (WellId) REFERENCES assay.Well(RowId),
    CONSTRAINT FK_WellGroupPositions_WellGroup FOREIGN KEY (WellGroupId) REFERENCES assay.WellGroup(RowId),
    CONSTRAINT UQ_WellGroupPositions_WellGroup_Well UNIQUE (WellGroupId, WellId)
);

CREATE TABLE assay.PlateProperty
(
    RowId INT IDENTITY(1,1),
    PlateId INT NOT NULL,
    PropertyId INT NOT NULL,
    PropertyURI NVARCHAR(300) NOT NULL,

    CONSTRAINT PK_PlateProperty PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateProperty_PlateId_PropertyId UNIQUE (PlateId, PropertyId),
    CONSTRAINT FK_PlateProperty_PlateId FOREIGN KEY (PlateId) REFERENCES assay.Plate(RowId),
    CONSTRAINT FK_PlateProperty_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor(PropertyId)
);
