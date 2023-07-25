CREATE TABLE exp.DataTypeExclusion
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    DataTypeRowId INT NOT NULL,
    DataType NVARCHAR(20) NOT NULL,
    ExcludedContainer EntityId NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    CONSTRAINT PK_DataTypeExclusion PRIMARY KEY (RowId),
    CONSTRAINT UQ_DataTypeExclusion_LSID UNIQUE (DataTypeRowId, DataType, ExcludedContainer)
);