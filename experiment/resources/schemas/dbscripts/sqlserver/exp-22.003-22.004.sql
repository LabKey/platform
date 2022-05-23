CREATE TABLE exp.ObjectLegacyNames
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    ObjectId INT NOT NULL,
    Name NVARCHAR(200) NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    CONSTRAINT PK_ObjectLegacyNames PRIMARY KEY (RowId),
    CONSTRAINT FK_ObjectLegacyNames_ObjectId FOREIGN KEY (ObjectId) REFERENCES exp.Object (ObjectId)
);
GO
ALTER TABLE exp.Material ADD MaterialSourceId INT NULL;
GO
UPDATE exp.Material SET MaterialSourceId = (
    SELECT distinct rowId FROM exp.materialSource WHERE materialSource.lsid = Material.cpastype
) WHERE Material.cpastype <> 'Material';
GO
EXEC core.executeJavaUpgradeCode 'addProvisionedSampleNameTypeId';