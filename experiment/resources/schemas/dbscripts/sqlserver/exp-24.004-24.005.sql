DROP INDEX ix_material_cpastype on exp.material;
CREATE INDEX ix_material_cpastype ON exp.material (cpastype, rowid);