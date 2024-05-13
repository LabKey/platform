CREATE TABLE exp.MaterialAncestors
(
    RowId INT NOT NULL,
    AncestorRowId INT NOT NULL,
    AncestorTypeId VARCHAR(11),

    CONSTRAINT FK_MaterialAncestors_MaterialId FOREIGN KEY (RowId) REFERENCES exp.Material (RowId) ON DELETE CASCADE
);

CREATE UNIQUE INDEX UQ_MaterialAncestors_AncestorTypeId_RowId ON exp.MaterialAncestors (AncestorTypeId, RowId);
CREATE INDEX IDX_MaterialAncestors_TargetId_MaterialId_TargetRowId ON exp.MaterialAncestors (AncestorTypeId, AncestorRowId, RowId);

EXEC core.executeJavaUpgradeCode 'populateMaterialAncestors';

CREATE TABLE exp.DataAncestors
(
    RowId INT NOT NULL,
    AncestorRowId INT NOT NULL,
    AncestorTypeId VARCHAR(11),

    CONSTRAINT FK_DataAncestors_DataId FOREIGN KEY (RowId) REFERENCES exp.Data (RowId) ON DELETE CASCADE
);

CREATE UNIQUE INDEX UQ_DataAncestors_AncestorTypeId_RowId ON exp.DataAncestors (AncestorTypeId, RowId);
CREATE INDEX IDX_DataAncestors_TargetId_MaterialId_TargetRowId ON exp.DataAncestors (AncestorTypeId, AncestorRowId, RowId);

EXEC core.executeJavaUpgradeCode 'populateDataAncestors';

