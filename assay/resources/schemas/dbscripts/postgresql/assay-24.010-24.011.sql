ALTER TABLE assay.plate ADD Barcode VARCHAR(255);
ALTER TABLE assay.plate ADD CONSTRAINT UQ_Barcode UNIQUE (Barcode);

UPDATE assay.plate SET Barcode = LPAD(rowid::text, 9, '0') WHERE plate.Barcode IS NULL AND plate.template IS FALSE;

ALTER TABLE assay.plate ADD CONSTRAINT check_template_true_barcode_null CHECK (NOT plate.template OR plate.Barcode IS NULL);

SELECT core.executeJavaUpgradeCode('updateBarcodeSequence');
