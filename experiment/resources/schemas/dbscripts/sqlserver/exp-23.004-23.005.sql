EXEC core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IDX_exp_material_recompute';

EXEC core.fn_dropifexists 'Material', 'exp', 'CONSTRAINT', 'DF_recomputeRollup';

ALTER TABLE exp.Material DROP COLUMN RecomputeRollup;