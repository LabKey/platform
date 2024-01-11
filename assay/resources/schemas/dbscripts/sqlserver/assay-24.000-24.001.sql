CREATE TABLE assay.PlateSet
(
    RowId INT NOT NULL,
    Name NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Created DATETIME NOT NULL,
    CreatedBy USERID NOT NULL,
    Modified DATETIME NOT NULL,
    ModifiedBy USERID NOT NULL,
    Archived BIT NOT NULL DEFAULT 0,

    CONSTRAINT PK_PlateSet PRIMARY KEY (RowId)
);

-- Insert a row into the plate set table for every plate in the system, store the plate row ID in the plate set table
-- in order to create the FK from the plate to plate set table
INSERT INTO assay.PlateSet (RowId, Name, Container, Created, CreatedBy, Modified, ModifiedBy)
SELECT RowId, 'TempPlateSet', Container, getdate(), CreatedBy, getdate(), ModifiedBy FROM assay.Plate;

-- Add the plate set field to the plate table and populate it with the plate set row ID
ALTER TABLE assay.Plate ADD PlateSet INT;
GO

UPDATE assay.Plate SET PlateSet = Rowid;
ALTER TABLE assay.plate ALTER COLUMN PlateSet INT NOT NULL;
ALTER TABLE assay.Plate ADD CONSTRAINT FK_Plate_PlateSet FOREIGN KEY (PlateSet) REFERENCES assay.PlateSet (RowId);
CREATE INDEX IX_Plate_PlateSet ON assay.Plate (PlateSet);

-- Run the java upgrade script to update plate set and plate tables to create the name expression based name values
EXEC core.executeJavaUpgradeCode 'updatePlateSetNames';