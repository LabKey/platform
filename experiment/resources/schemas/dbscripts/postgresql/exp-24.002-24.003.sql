CREATE TABLE exp.MaterialAncestors
(
    RowId INT NOT NULL,
    AncestorRowId INT NOT NULL,
    AncestorTypeId VARCHAR(11),

    CONSTRAINT FK_MaterialAncestors_MaterialId FOREIGN KEY (RowId) REFERENCES exp.Material (RowId) ON DELETE CASCADE
);

SELECT core.executeJavaUpgradeCode('populateMaterialAncestors');

CREATE UNIQUE INDEX UQ_MaterialAncestors_AncestorTypeId_RowId ON exp.MaterialAncestors (AncestorTypeId, RowId);
CREATE INDEX IDX_MaterialAncestors_AncestorTypeId_RowId_AncestorRowId ON exp.MaterialAncestors (AncestorTypeId, RowId, AncestorRowId);


CREATE TABLE exp.DataAncestors
(
    RowId INT NOT NULL,
    AncestorRowId INT NOT NULL,
    AncestorTypeId VARCHAR(11),

    CONSTRAINT FK_DataAncestors_DataId FOREIGN KEY (RowId) REFERENCES exp.Data (RowId) ON DELETE CASCADE
);

SELECT core.executeJavaUpgradeCode('populateDataAncestors');

CREATE UNIQUE INDEX UQ_DataAncestors_AncestorTypeId_RowId ON exp.DataAncestors (AncestorTypeId, RowId);
CREATE INDEX IDX_DataAncestors_AncestorTypeId_RowId_AncestorRowId ON exp.DataAncestors (AncestorTypeId, RowId, AncestorRowId);

