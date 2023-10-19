ALTER TABLE exp.material ADD rootmaterialrowid INT NULL
GO

UPDATE exp.material SET rootmaterialrowid = (
    SELECT DISTINCT rowid FROM exp.material expmat WHERE rootmateriallsid = expmat.lsid
)
GO

ALTER TABLE exp.material ALTER COLUMN rootmaterialrowid INT NOT NULL
GO

CREATE INDEX uq_material_rootrowid ON exp.material (rootmaterialrowid)
GO

EXEC core.executeJavaUpgradeCode 'addRowIdToMaterializedSampleTypes';