ALTER TABLE assay.plateset ADD LSID LSIDtype;
GO

EXEC core.executeJavaUpgradeCode 'addLsidToPlateSets';

ALTER TABLE assay.plateset ALTER COLUMN LSID LSIDType NOT NULL;
GO
