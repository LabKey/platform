DROP INDEX IF EXISTS exp.ix_material_cpastype;
CREATE INDEX ix_material_cpastype ON exp.material (cpastype, rowid);