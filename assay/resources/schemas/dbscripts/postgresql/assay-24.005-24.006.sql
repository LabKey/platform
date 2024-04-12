ALTER TABLE assay.Hit ADD COLUMN PlateSetPath VARCHAR (4000);

-- Populate paths of pre-existing hits
SELECT core.executeJavaUpgradeCode('populatePlateSetPaths');

ALTER TABLE assay.Hit ALTER COLUMN PlateSetPath SET NOT NULL;
