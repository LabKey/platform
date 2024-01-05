CREATE TABLE assay.PlateSet
(
    RowId INT IDENTITY(1,1),
    Name NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Created DATETIME NOT NULL,
    CreatedBy USERID NOT NULL,
    Modified DATETIME NOT NULL,
    ModifiedBy USERID NOT NULL,
    Archived BIT NOT NULL DEFAULT 0,
    PlateId INT NOT NULL,                       -- temporary

    CONSTRAINT PK_PlateSet PRIMARY KEY (RowId)
);

-- Insert a row into the plate set table for every plate in the system, store the plate row ID in the plate set table
-- in order to create the FK from the plate to plate set table
INSERT INTO assay.PlateSet (Name, Container, Created, CreatedBy, Modified, ModifiedBy, PlateId)
SELECT 'TempPlateSet', Container, getdate(), CreatedBy, getdate(), ModifiedBy, RowId FROM assay.Plate;

-- Add the plate set field to the plate table and populate it with the plate set row ID
ALTER TABLE assay.Plate ADD PlateSet INT;
GO

UPDATE assay.Plate SET PlateSet = (SELECT PS.RowID FROM assay.PlateSet PS WHERE Plate.RowId = PlateId);
ALTER TABLE assay.plate ALTER COLUMN PlateSet INT NOT NULL;

-- Add the FK to the plate table and drop the temporary plate ID column in the plate set table
ALTER TABLE assay.Plate ADD CONSTRAINT FK_Plate_PlateSet FOREIGN KEY (PlateSet) REFERENCES assay.PlateSet (RowId);
ALTER TABLE assay.PlateSet DROP COLUMN PlateId;
CREATE INDEX IX_Plate_PlateSet ON assay.Plate (PlateSet);

-- Run the java upgrade script to update plate set and plate tables to create the name expression based name values
EXEC core.executeJavaUpgradeCode 'updatePlateSetNames';