ALTER TABLE assay.PlateSet ADD COLUMN PlateSetPath VARCHAR (4000);
ALTER TABLE assay.PlateSet ADD CONSTRAINT UQ_PlateSet_PlateSetPath UNIQUE (PlateSetPath);

-- stand-alone assay plate sets
UPDATE assay.PlateSet
    SET PlateSetPath = '/' || RowId ||'/'
    WHERE RootPlateSetId IS NULL;

-- top-level primary plate sets
UPDATE assay.PlateSet
    SET PlateSetPath = '/' || RowId || '/'
    WHERE PlateSetPath IS NULL AND PrimaryPlateSetId IS NULL AND RootPlateSetId = RowId;

-- direct assay plate set descendants of top-level primary plate sets
UPDATE assay.PlateSet
    SET PlateSetPath = '/' || RootPlateSetId || '/' || RowId || '/'
    WHERE PlateSetPath IS NULL AND RootPlateSetId IS NOT NULL AND (
        RootPlateSetId = PrimaryPlateSetId OR PrimaryPlateSetId IS NULL
    );

-- Handle deeply nested plate sets
SELECT core.executeJavaUpgradeCode('populatePlateSetPaths');

ALTER TABLE assay.Hit ADD COLUMN PlateSetPath VARCHAR (4000);

-- Populate paths of pre-existing hits
UPDATE assay.Hit AS H SET PlateSetPath = (
    SELECT PS.PlateSetPath
    FROM assay.PlateSet AS PS
    INNER JOIN assay.Plate AS P ON P.PlateSet = PS.RowId
    INNER JOIN assay.Well AS W ON W.PlateId = P.RowId
    WHERE W.Lsid = H.WellLsid
);

ALTER TABLE assay.Hit ALTER COLUMN PlateSetPath SET NOT NULL;
