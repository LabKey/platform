CREATE TABLE exp.MaterialIndexed
(
    MaterialId INT NOT NULL,
    LastIndexed DATETIME NOT NULL,

    CONSTRAINT PK_MaterialIndexing PRIMARY KEY (MaterialId),
    CONSTRAINT FK_MaterialId FOREIGN KEY (MaterialId) REFERENCES exp.Material (RowId)
);

INSERT INTO exp.MaterialIndexed (MaterialId, LastIndexed) (SELECT RowId, LastIndexed FROM exp.Material WHERE LastIndexed IS NOT NULL);

ALTER TABLE exp.Material DROP COLUMN LastIndexed;


CREATE TABLE exp.DataIndexed
(
    DataId INT NOT NULL,
    LastIndexed DATETIME NOT NULL,

    CONSTRAINT PK_DataIndexing PRIMARY KEY (DataId),
    CONSTRAINT FK_DataId FOREIGN KEY (DataId) REFERENCES exp.Data (RowId)
);

INSERT INTO exp.DataIndexed (DataId, LastIndexed) (SELECT RowId, LastIndexed FROM exp.Data WHERE LastIndexed IS NOT NULL);

ALTER TABLE exp.Data DROP COLUMN LastIndexed;
