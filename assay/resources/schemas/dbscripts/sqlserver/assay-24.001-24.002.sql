CREATE TABLE assay.PlateType
(
    RowId INT IDENTITY(1,1),
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    Description NVARCHAR(200) NOT NULL,

    CONSTRAINT PK_PlateType PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateType_Rows_Cols UNIQUE (Rows, Columns)
);

INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (3, 4, '12 well (3x4)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (4, 6, '24 well (4x6)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (6, 8, '48 well (6x8)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (8, 12, '96 well (8x12)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (16, 24, '384 well (16x24)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (32, 48, '1536 well (32x48)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (0, 0, 'Invalid Plate Type (Plates which were created with non-valid row & column combinations)');

ALTER TABLE assay.Plate ADD PlateTypeId INT;
GO
ALTER TABLE assay.Plate ADD CONSTRAINT FK_Plate_PlateTypeId FOREIGN KEY (PlateTypeId) REFERENCES assay.PlateType (RowId);

UPDATE assay.Plate
SET PlateTypeId =
        CASE
            WHEN (Rows = 3 AND Columns = 4) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 3 AND Columns = 4)
            WHEN (Rows = 4 AND Columns = 6) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 4 AND Columns = 6)
            WHEN (Rows = 6 AND Columns = 8) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 6 AND Columns = 8)
            WHEN (Rows = 8 AND Columns = 12) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 8 AND Columns = 12)
            WHEN (Rows = 16 AND Columns = 24) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 16 AND Columns = 24)
            WHEN (Rows = 32 AND Columns = 48) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 32 AND Columns = 48)
            ELSE (SELECT RowId FROM assay.PlateType WHERE Rows = 0 AND Columns = 0)
            END
WHERE PlateTypeId IS NULL;

ALTER TABLE assay.Plate ALTER COLUMN PlateTypeId INT NOT NULL;
ALTER TABLE assay.Plate DROP COLUMN Rows;
ALTER TABLE assay.Plate DROP COLUMN Columns;