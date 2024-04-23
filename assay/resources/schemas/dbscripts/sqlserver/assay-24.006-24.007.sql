ALTER TABLE assay.PlateSet ADD Template BIT NOT NULL DEFAULT 0;

UPDATE assay.PlateSet SET Template = true WHERE RowId IN (
    SELECT PlateSet FROM assay.Plate WHERE Template = true
)
