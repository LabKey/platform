ALTER TABLE exp.Material ADD COLUMN RecomputeRollup BOOL NULL DEFAULT(FALSE);
ALTER TABLE exp.Material ADD COLUMN AliquotCount INTEGER NULL;
ALTER TABLE exp.Material ADD COLUMN AliquotVolume FLOAT NULL;
ALTER TABLE exp.Material ADD COLUMN AliquotUnit VARCHAR(10) NULL;

UPDATE exp.Material SET RecomputeRollup=TRUE WHERE rowId IN
                                                   (SELECT distinct parent.rowId
                                                    FROM exp.material aliquot
                                                             JOIN exp.material parent ON aliquot.rootMaterialLsid = parent.lsid
                                                   );

CREATE INDEX IDX_exp_material_recompute ON exp.Material (container, rowid, lsid) WHERE RecomputeRollup=TRUE;