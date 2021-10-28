ALTER TABLE exp.Material ADD RecomputeRollup BIT NULL CONSTRAINT DF_recomputeRollup DEFAULT(0);
ALTER TABLE exp.Material ADD AliquotCount INT NULL;
ALTER TABLE exp.Material ADD AliquotVolume FLOAT NULL;
ALTER TABLE exp.Material ADD AliquotUnit NVARCHAR(10) NULL;
GO
UPDATE exp.Material SET RecomputeRollup=1 WHERE lsid IN
                                                (
                                                    SELECT distinct rootMaterialLsid FROM exp.material
                                                );
GO
CREATE INDEX IDX_exp_material_recompute ON exp.Material (container, rowid, lsid) WHERE RecomputeRollup=1;
