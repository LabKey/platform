UPDATE exp.material
    SET rootmateriallsid = lsid
    WHERE rootmateriallsid IS NULL;

ALTER TABLE exp.material
    ALTER COLUMN rootmateriallsid SET NOT NULL;

CREATE INDEX IF NOT EXISTS uq_material_rootlsid
    on exp.material (rootmateriallsid);

-- this is duplicative of constraint uq_material_lsid
DROP INDEX IF EXISTS exp.idx_material_lsid;