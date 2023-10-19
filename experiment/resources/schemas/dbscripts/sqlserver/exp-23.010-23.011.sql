ALTER TABLE exp.material ADD rootmaterialrowid INTEGER NULL;
GO

UPDATE Material
    SET Material.rootmaterialrowid = Parent.rowid
    FROM exp.material Material
    INNER JOIN exp.material Parent ON Material.rootmateriallsid = Parent.lsid;
GO

ALTER TABLE exp.material ALTER COLUMN rootmaterialrowid INTEGER NOT NULL;
GO

CREATE INDEX ix_material_rootrowid ON exp.material (rootmaterialrowid);
GO

EXEC core.executeJavaUpgradeCode 'addRowIdToMaterializedSampleTypes';
