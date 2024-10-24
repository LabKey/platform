/*
    For LabKey 19.2 and earlier, the assayresult schema and the Plate, WellGroup, and Well tables were managed by the
    study module. As of 19.3, the assay module now manages these objects, with the tables moving from the "study" schema
    to the new "assay" schema.

    The assayresult and assay table creation statements have been removed from the study SQL scripts, but this script
    will run on servers where those objects were previously created and populated with data... and on servers where they
    weren't. We need to test for the existence of the assay tables in the study schema, moving them to the "assay" schema
    if present, otherwise creating them from scratch. We also create the assayresult schema conditionally.

    Once we no longer upgrade from 19.2, we can remove the conditionals below.
 */

CREATE SCHEMA assay
GO

IF (EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'study' AND TABLE_NAME = 'Plate'))
    BEGIN

        ALTER SCHEMA assay TRANSFER study.Plate;
        ALTER SCHEMA assay TRANSFER study.WellGroup;
        ALTER SCHEMA assay TRANSFER study.Well;

    END
ELSE
    BEGIN

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

    END
GO

IF NOT EXISTS(SELECT * FROM sys.Schemas WHERE name = 'assayresult')
    BEGIN
        EXEC ('CREATE SCHEMA assayresult');
    END
GO

-- Repackage AssayDesignerRole from study to assay
UPDATE core.RoleAssignments SET Role = 'org.labkey.assay.security.AssayDesignerRole' WHERE Role = 'org.labkey.study.security.roles.AssayDesignerRole';
GO

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

-- populate the WellGroupPositions table
-- Each well is associated with it's well group using a PropertyDescriptor that
-- has a PropertyURI with namespace prefix "WellTemplate" and objectId of "objectType#Group N"
-- where the property value is the LSID of the well group it belongs to.
INSERT INTO assay.WellGroupPositions (wellId, wellGroupId)
SELECT
    w.rowid, wg.rowid
FROM assay.Well w
         INNER JOIN exp.Object o ON o.ObjectUri = w.lsid
         INNER JOIN exp.ObjectProperty op ON o.objectId = op.objectId
         INNER JOIN exp.PropertyDescriptor pd ON op.propertyId = pd.propertyId
         INNER JOIN assay.WellGroup wg ON wg.lsid = op.stringValue
WHERE pd.propertyuri LIKE 'urn:lsid:%:WellTemplate.Folder-%:objectType#Group %';

-- The WellTemplate "Group N" properties should be unused now.
-- We can safely delete the ObjectProperty values and PropertyDescriptors
DELETE FROM exp.ObjectProperty WHERE propertyId IN (
    SELECT propertyId
    FROM exp.PropertyDescriptor
    WHERE propertyUri LIKE 'urn:lsid:%:WellTemplate.Folder-%:objectType#Group %'
);

DELETE FROM exp.propertydescriptor WHERE
        propertyUri LIKE 'urn:lsid:%:WellTemplate.Folder-%:objectType#Group %';

ALTER TABLE assay.plate ADD
    Modified DATETIME,
    ModifiedBy USERID;
GO

UPDATE assay.plate SET Modified = Created,
                       ModifiedBy = CreatedBy;
GO

ALTER TABLE assay.plate ALTER COLUMN Modified DATETIME NOT NULL;
ALTER TABLE assay.plate ALTER COLUMN ModifiedBy USERID NOT NULL;

ALTER TABLE assay.plate
    ADD CONSTRAINT uq_plate_lsid UNIQUE (lsid);

ALTER TABLE assay.well
    ADD CONSTRAINT uq_well_lsid UNIQUE (lsid);

ALTER TABLE assay.wellgroup
    ADD CONSTRAINT uq_wellgroup_lsid UNIQUE (lsid);

-- Issue 39805: uq_plate_container_name violation when upgrading via assay-20.001-20.002.sql
-- plate names are unique in each container
--ALTER TABLE assay.plate
--    ADD CONSTRAINT uq_plate_container_name UNIQUE (container, name);

-- each well position is unique on the plate
ALTER TABLE assay.well
    ADD CONSTRAINT uq_well_plateid_row_col UNIQUE (plateid, row, col);

-- well group names must be unique within each well group type
ALTER TABLE assay.wellgroup
    ADD CONSTRAINT uq_wellgroup_plateid_typename_name UNIQUE (plateid, typename, name);

-- Issue 39805: uq_plate_container_name violation when upgrading via assay-20.001-20.002.sql
EXEC core.fn_dropifexists 'plate', 'assay', 'CONSTRAINT', 'uq_plate_container_name';

-- plate template (not instances) names are unique in each container
CREATE UNIQUE INDEX uq_plate_container_name_template ON assay.plate (container, name) WHERE template=1;