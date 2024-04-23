ALTER TABLE assay.PlateSet ADD Template BIT NOT NULL DEFAULT 0;

UPDATE assay.Plate SET Template = 0 WHERE Template = 1;
