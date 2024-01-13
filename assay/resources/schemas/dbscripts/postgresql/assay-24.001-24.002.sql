CREATE TABLE assay.PlateType
(
    RowId SERIAL,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    Description VARCHAR(200) NOT NULL,

    CONSTRAINT PK_PlateType PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateType_Rows_Cols UNIQUE (Rows, Columns)
);

INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (3, 4, '12 well (3x4)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (4, 6, '24 well (4x6)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (6, 8, '48 well (6x8)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (8, 12, '96 well (8x12)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (16, 24, '384 well (16x24)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (32, 48, '1536 well (32x48)');

ALTER TABLE assay.Plate ADD COLUMN PlateTypeId INTEGER;
ALTER TABLE assay.Plate ADD CONSTRAINT FK_Plate_PlateTypeId FOREIGN KEY (PlateTypeId) REFERENCES assay.PlateType (RowId);
