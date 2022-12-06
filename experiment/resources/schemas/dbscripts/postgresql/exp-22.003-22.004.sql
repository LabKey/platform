CREATE TABLE exp.ObjectLegacyNames
(
    RowId SERIAL NOT NULL,
    ObjectId INT NOT NULL,
    ObjectType VARCHAR(20) NOT NULL,
    Name VARCHAR(200) NOT NULL,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_ObjectLegacyNames PRIMARY KEY (RowId)
);

ALTER TABLE exp.Material ADD COLUMN MaterialSourceId INT NULL;
UPDATE exp.Material SET MaterialSourceId = (
        SELECT distinct rowId FROM exp.materialSource WHERE materialSource.lsid = Material.cpastype
    ) WHERE Material.cpastype <> 'Material';

CREATE INDEX IDX_material_name_sourceid ON exp.Material (name, materialSourceId);

SELECT core.executeJavaUpgradeCode('addProvisionedSampleName');