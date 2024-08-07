ALTER TABLE assay.plate ADD Barcode VARCHAR(400);

ALTER TABLE assay.plate ADD CONSTRAINT UQ_Barcode UNIQUE (Barcode);

UPDATE assay.plate SET Barcode = FORMAT(rowid, '000000000') WHERE Barcode IS NULL AND template = 0;

ALTER TABLE assay.plate ADD CONSTRAINT check_template_true_barcode_null CHECK (NOT template OR Barcode IS NULL);


EXEC core.executeJavaUpgradeCode 'updateBarcodeSequence';
