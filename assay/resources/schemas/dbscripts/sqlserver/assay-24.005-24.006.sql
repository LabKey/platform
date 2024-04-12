ALTER TABLE assay.Hit ADD PlateSetPath NVARCHAR (4000);
GO

-- Populate paths of pre-existing hits
EXEC core.executeJavaUpgradeCode 'populatePlateSetPaths';

ALTER TABLE assay.Hit ALTER COLUMN PlateSetPath NVARCHAR (4000) NOT NULL;
GO
