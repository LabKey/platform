CREATE TABLE exp.ObjectUsedNames
(
    RowId SERIAL NOT NULL,
    ObjectId INT NOT NULL,
    Name VARCHAR(200) NOT NULL,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_ObjectUsedNames PRIMARY KEY (RowId),
    CONSTRAINT FK_ObjectUsedNames_ObjectId FOREIGN KEY (ObjectId) REFERENCES exp.Object (ObjectId)
);

ALTER TABLE exp.Material ADD COLUMN MaterialSourceId INT NULL;
UPDATE exp.Material SET MaterialSourceId = (
        SELECT distinct rowId FROM exp.materialSource WHERE materialSource.lsid = Material.cpastype
    ) WHERE Material.cpastype <> 'Material';

SELECT core.executeJavaUpgradeCode('addProvisionedSampleNameTypeId');