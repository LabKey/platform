/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

CREATE TABLE exp.DataColors
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    Label VARCHAR(64) NOT NULL,
    Color VARCHAR(7) NOT NULL,
    Archived BOOLEAN NOT NULL DEFAULT FALSE,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_DataColors PRIMARY KEY (RowId),
    CONSTRAINT UQ_DataColors_Label UNIQUE (Container, Label)
);

ALTER TABLE exp.Material ADD COLUMN ExpMaterialColor INT;
ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_ExpMaterialColor FOREIGN KEY (ExpMaterialColor) REFERENCES exp.DataColors (RowId);

CREATE TABLE exp.DataTypeColorExclusion
(
    RowId SERIAL NOT NULL,
    DataTypeRowId INT NOT NULL,
    DataType VARCHAR(20) NOT NULL,
    ColorRowId INT NOT NULL,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_DataTypeColorExclusion PRIMARY KEY (RowId),
    CONSTRAINT UQ_DataTypeColorExclusion UNIQUE (DataTypeRowId, DataType, ColorRowId)
);
