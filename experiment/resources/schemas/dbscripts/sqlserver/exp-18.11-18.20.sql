/* exp-18.11-18.12.sql */

ALTER TABLE exp.PropertyDescriptor
    ADD TextExpression nvarchar(200) NULL

/* exp-18.12-18.13.sql */

CREATE TABLE exp.Exclusions
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    RunId INT NOT NULL,
    Comment TEXT NULL,
    Created DATETIME  NULL,
    CreatedBy INT NULL,
    Modified DATETIME  NULL,
    ModifiedBy INT NULL,
    CONSTRAINT PK_Exclusion_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_Exclusion_RunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId)
);
CREATE INDEX IX_Exclusion_RunId ON exp.Exclusions(RunId);

CREATE TABLE exp.ExclusionMaps
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    ExclusionId INT NOT NULL,
    DataRowId INT NOT NULL,
    Created DATETIME  NULL,
    CreatedBy INT NULL,
    Modified DATETIME  NULL,
    ModifiedBy INT NULL,
    CONSTRAINT PK_ExclusionMap_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_ExclusionMap_ExclusionId FOREIGN KEY (ExclusionId) REFERENCES exp.Exclusions (RowId),
    CONSTRAINT UQ_ExclusionMap_ExclusionId_DataId UNIQUE (ExclusionId, DataRowId)
);

/* exp-18.13-18.14.sql */

ALTER TABLE exp.DomainDescriptor ALTER COLUMN DomainURI NVARCHAR(300) NOT NULL;
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN PropertyURI NVARCHAR(300) NOT NULL;