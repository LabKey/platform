CREATE TABLE assay.PlateSet
(
    RowId SERIAL,
    Name VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Created TIMESTAMP NOT NULL,
    CreatedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Archived BOOLEAN NOT NULL DEFAULT FALSE,
    PlateId INT NOT NULL,                       -- temporary

    CONSTRAINT PK_PlateSet PRIMARY KEY (RowId)
);

-- Insert a row into the plate set table for every plate in the system, store the plate row ID in the plate set table
-- in order to create the FK from the plate to plate set table
INSERT INTO assay.PlateSet (Name, Container, Created, CreatedBy, Modified, ModifiedBy, PlateId)
    SELECT 'TempPlateSet', Container, now(), CreatedBy, now(), ModifiedBy, RowId FROM assay.Plate;

-- Add the plate set field to the plate table and populate it with the plate set row ID
ALTER TABLE assay.Plate ADD COLUMN PlateSet INTEGER;
UPDATE assay.Plate SET PlateSet = (SELECT PS.RowID FROM assay.PlateSet PS WHERE Plate.RowId = PlateId);
ALTER TABLE assay.plate ALTER PlateSet SET NOT NULL;

-- Add the FK to the plate table
ALTER TABLE assay.Plate ADD CONSTRAINT FK_Plate_PlateSet FOREIGN KEY (PlateSet) REFERENCES assay.PlateSet (RowId);

-- Drop the temporary plate ID column in the plate set table
ALTER TABLE assay.PlateSet DROP COLUMN PlateId;

-- Run the java upgrade script to update plate set and plate tables to create the name expression based name values