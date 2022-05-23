CREATE TABLE exp.ObjectLegacyNames
(
    RowId SERIAL NOT NULL,
    ObjectId INT NOT NULL,
    Name VARCHAR(200) NOT NULL,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_ObjectLegacyNames PRIMARY KEY (RowId),
    CONSTRAINT FK_ObjectLegacyNames_ObjectId FOREIGN KEY (ObjectId) REFERENCES exp.Object (ObjectId)
);

ALTER TABLE exp.Material ADD COLUMN MaterialSourceId INT NULL;
UPDATE exp.Material SET MaterialSourceId = (
        SELECT distinct rowId FROM exp.materialSource WHERE materialSource.lsid = Material.cpastype
    ) WHERE Material.cpastype <> 'Material';

SELECT core.executeJavaUpgradeCode('addProvisionedSampleNameTypeId');