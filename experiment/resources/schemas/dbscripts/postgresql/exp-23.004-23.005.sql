SELECT core.fn_dropifexists('Material', 'exp', 'INDEX', 'IDX_exp_material_recompute');

ALTER TABLE exp.Material DROP COLUMN RecomputeRollup;
