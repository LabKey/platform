ALTER TABLE assay.PlateSet ADD Template BIT NOT NULL DEFAULT 0;
UPDATE assay.Plate SET Template = 0 WHERE Template = 1;

ALTER TABLE assay.Plate ADD Archived BIT NOT NULL DEFAULT 0;

UPDATE assay.Plate SET AssayType = 'Standard' WHERE AssayType IS NULL;
ALTER TABLE assay.Plate ALTER COLUMN AssayType NVARCHAR(200) NOT NULL;
