CREATE TABLE assay.PlateType
(
    RowId SERIAL,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    Description VARCHAR(300) NOT NULL,
    Archived BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_PlateType PRIMARY KEY (RowId),
    CONSTRAINT UQ_PlateType_Rows_Cols UNIQUE (Rows, Columns)
);

INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (3, 4, '12 well (3x4)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (4, 6, '24 well (4x6)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (6, 8, '48 well (6x8)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (8, 12, '96 well (8x12)');
INSERT INTO assay.PlateType (Rows, Columns, Description) VALUES (16, 24, '384 well (16x24)');
INSERT INTO assay.PlateType (Rows, Columns, Description, Archived) VALUES (32, 48, '1536 well (32x48)', TRUE);
INSERT INTO assay.PlateType (Rows, Columns, Description, Archived) VALUES (0, 0, 'Invalid Plate Type (Plates which were created with non-valid row & column combinations)', TRUE);

-- Rename type column to assayType
ALTER TABLE assay.Plate RENAME COLUMN Type TO AssayType;
-- Add plateType as a FK to assay.PlateType
ALTER TABLE assay.Plate ADD COLUMN PlateType INTEGER;
ALTER TABLE assay.Plate ADD CONSTRAINT FK_Plate_PlateType FOREIGN KEY (PlateType) REFERENCES assay.PlateType (RowId);

-- Add ID and description columns to Plate and PlateSet tables
ALTER TABLE assay.Plate ADD COLUMN PlateId VARCHAR(200);
ALTER TABLE assay.Plate ADD COLUMN Description VARCHAR(300);
ALTER TABLE assay.PlateSet ADD COLUMN PlateSetId VARCHAR(200);
ALTER TABLE assay.PlateSet ADD COLUMN Description VARCHAR(300);

-- Most existing plate sets will have a generated name, but mutated ones will get fixed up by the java upgrade script
UPDATE assay.PlateSet SET PlateSetId = Name;

UPDATE assay.Plate
SET PlateType =
        CASE
            WHEN (Rows = 3 AND Columns = 4) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 3 AND Columns = 4)
            WHEN (Rows = 4 AND Columns = 6) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 4 AND Columns = 6)
            WHEN (Rows = 6 AND Columns = 8) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 6 AND Columns = 8)
            WHEN (Rows = 8 AND Columns = 12) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 8 AND Columns = 12)
            WHEN (Rows = 16 AND Columns = 24) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 16 AND Columns = 24)
            WHEN (Rows = 32 AND Columns = 48) THEN (SELECT RowId FROM assay.PlateType WHERE Rows = 32 AND Columns = 48)
            ELSE (SELECT RowId FROM assay.PlateType WHERE Rows = 0 AND Columns = 0)
            END
WHERE PlateType IS NULL;

ALTER TABLE assay.Plate ALTER COLUMN PlateType SET NOT NULL;
ALTER TABLE assay.Plate DROP COLUMN Rows;
ALTER TABLE assay.Plate DROP COLUMN Columns;

-- upgrade script to initialize plate and plateSet IDs
SELECT core.executeJavaUpgradeCode('initializePlateAndPlateSetIDs');

-- finalize plate and plateSet ID columns
ALTER TABLE assay.Plate ALTER COLUMN PlateId SET NOT NULL;
ALTER TABLE assay.Plate ADD CONSTRAINT UQ_Plate_PlateId UNIQUE (PlateId);

ALTER TABLE assay.PlateSet ALTER COLUMN PlateSetId SET NOT NULL;
ALTER TABLE assay.PlateSet ADD CONSTRAINT UQ_PlateSet_PlateSetId UNIQUE (PlateSetId);
