ALTER TABLE exp.Material ADD RecomputeRollup BIT NOT NULL CONSTRAINT DF_recomputeRollup DEFAULT(0);
ALTER TABLE exp.Material ADD AliquotCount INT NULL;
ALTER TABLE exp.Material ADD AliquotVolume FLOAT NULL;
ALTER TABLE exp.Material ADD AliquotUnit NVARCHAR(10) NULL;
GO
UPDATE exp.Material SET RecomputeRollup=1 WHERE rowId IN
                                                (SELECT distinct parent.rowId
                                                 FROM exp.material aliquot
                                                          JOIN exp.material parent ON aliquot.rootMaterialLsid = parent.lsid
                                                );
GO
CREATE INDEX IDX_exp_material_recompute ON exp.Material (container, rowid, lsid) WHERE RecomputeRollup=1;
