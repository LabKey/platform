ALTER TABLE assay.plateset ADD COLUMN LSID LSIDtype;

SELECT core.executeJavaUpgradeCode('addLsidToPlateSets');

ALTER TABLE assay.plateset ALTER COLUMN LSID SET NOT NULL;
