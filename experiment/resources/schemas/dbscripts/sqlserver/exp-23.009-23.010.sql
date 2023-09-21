UPDATE exp.material
    SET rootmateriallsid = lsid
    WHERE rootmateriallsid IS NULL;

ALTER TABLE exp.material
    ALTER COLUMN rootmateriallsid LSIDtype NOT NULL;

CREATE INDEX uq_material_rootlsid
    on exp.material (rootmateriallsid);

-- this is duplicative of constraint uq_material_lsid
EXEC core.fn_dropifexists 'material', 'exp', 'INDEX', 'idx_material_lsid';