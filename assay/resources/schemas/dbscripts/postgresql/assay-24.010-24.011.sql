ALTER TABLE assay.plate ADD Barcode VARCHAR(400);
ALTER TABLE assay.plate ADD CONSTRAINT UQ_Barcode UNIQUE (Barcode);

UPDATE assay.plate SET Barcode = LPAD(rowid::text, 9, '0') WHERE plate.Barcode IS NULL AND plate.template IS FALSE;

SELECT core.executeJavaUpgradeCode('updateBarcodeSequence');
