ALTER TABLE assay.PlateSet DROP COLUMN PlateSetPath;
GO

ALTER TABLE assay.PlateSet ADD CONSTRAINT UQ_PlateSet_PlateSetPath UNIQUE (PlateSetPath);
GO

ALTER TABLE assay.PlateSet ADD PlateSetPath NVARCHAR (4000);
GO

-- stand-alone assay plate sets
UPDATE assay.PlateSet
    SET PlateSetPath = CONCAT('/', RowId, '/')
    WHERE RootPlateSetId IS NULL;

-- top-level primary plate sets
UPDATE assay.PlateSet
    SET PlateSetPath = CONCAT('/', RowId, '/')
    WHERE PlateSetPath IS NULL AND PrimaryPlateSetId IS NULL AND RootPlateSetId = RowId;

-- direct assay plate set descendants of top-level primary plate sets
UPDATE assay.PlateSet
    SET PlateSetPath = CONCAT('/', RootPlateSetId, '/', RowId, '/')
    WHERE PlateSetPath IS NULL AND RootPlateSetId IS NOT NULL AND (
        RootPlateSetId = PrimaryPlateSetId OR PrimaryPlateSetId IS NULL
    );

-- Handle deeply nested plate sets
EXEC core.executeJavaUpgradeCode 'populatePlateSetPaths';

ALTER TABLE assay.Hit ADD PlateSetPath NVARCHAR (4000);
GO

-- Populate paths of pre-existing hits
UPDATE assay.Hit SET PlateSetPath = (
    SELECT PlateSet.PlateSetPath
    FROM assay.PlateSet
    INNER JOIN assay.Plate ON Plate.PlateSet = PlateSet.RowId
    INNER JOIN assay.Well ON Well.PlateId = Plate.RowId
    WHERE Well.Lsid = Hit.WellLsid
);

ALTER TABLE assay.Hit ALTER COLUMN PlateSetPath NVARCHAR (4000) NOT NULL;
GO
