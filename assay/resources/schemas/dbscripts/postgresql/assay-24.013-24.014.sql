ALTER TABLE assay.plateset ADD COLUMN LSID VARCHAR(200);

SELECT core.executeJavaUpgradeCode('addLsidToPlateSets');

ALTER TABLE assay.plateset ALTER COLUMN LSID SET NOT NULL;
