ALTER TABLE assay.PlateSet ADD COLUMN Template BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE assay.Plate SET Template = False WHERE Template = True;
