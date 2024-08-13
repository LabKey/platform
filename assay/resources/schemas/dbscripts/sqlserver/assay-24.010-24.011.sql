ALTER TABLE assay.plate ADD Barcode NVARCHAR(255);
GO
CREATE UNIQUE NONCLUSTERED INDEX UQ_Barcode ON assay.plate(Barcode) WHERE Barcode IS NOT NULL;
GO

UPDATE assay.plate
SET Barcode = RIGHT(REPLICATE('0', 9) + CAST(rowid AS VARCHAR(9)), 9)
WHERE Barcode IS NULL AND template = 0;

ALTER TABLE assay.plate ADD CONSTRAINT check_template_true_barcode_null CHECK ((template = 0) OR Barcode IS NULL);

EXEC core.executeJavaUpgradeCode 'updateBarcodeSequence';
