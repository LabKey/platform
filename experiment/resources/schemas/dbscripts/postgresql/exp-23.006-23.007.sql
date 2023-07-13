CREATE TABLE exp.DataTypeExclusion
(
    RowId SERIAL NOT NULL,
    DataTypeRowId INT NOT NULL,
    DataType VARCHAR(20) NOT NULL,
    ExcludedContainer ENTITYID NOT NULL,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_DataTypeExclusion PRIMARY KEY (RowId),
    CONSTRAINT UQ_DataTypeExclusion UNIQUE (DataTypeRowId, DataType, ExcludedContainer)
);