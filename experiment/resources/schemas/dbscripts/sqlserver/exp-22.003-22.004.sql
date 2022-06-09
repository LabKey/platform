CREATE TABLE exp.ObjectLegacyNames
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    ObjectId INT NOT NULL,
    ObjectType NVARCHAR(20) NOT NULL,
    Name NVARCHAR(200) NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    CONSTRAINT PK_ObjectLegacyNames PRIMARY KEY (RowId)
);
GO
ALTER TABLE exp.Material ADD MaterialSourceId INT NULL;
GO
UPDATE exp.Material SET MaterialSourceId = (
    SELECT distinct rowId FROM exp.materialSource WHERE materialSource.lsid = Material.cpastype
) WHERE Material.cpastype <> 'Material';
GO
CREATE INDEX IDX_material_name_sourceid ON exp.Material (name, materialSourceId);
GO
EXEC core.executeJavaUpgradeCode 'addProvisionedSampleName';