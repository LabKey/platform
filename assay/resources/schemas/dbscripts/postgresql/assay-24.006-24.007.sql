ALTER TABLE assay.PlateSet ADD COLUMN Template BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE assay.PlateSet SET Template = true WHERE RowId IN (
    SELECT PlateSet FROM assay.Plate WHERE Template = true
)
