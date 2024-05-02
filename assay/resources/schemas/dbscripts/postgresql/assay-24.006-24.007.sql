ALTER TABLE assay.PlateSet ADD COLUMN Template BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE assay.Plate SET Template = False WHERE Template = True;

ALTER TABLE assay.Plate ADD COLUMN Archived BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE assay.Plate SET AssayType = 'Standard' WHERE AssayType IS NULL;
ALTER TABLE assay.Plate ALTER COLUMN AssayType SET NOT NULL;
