-- (postgres doesn't have the null inconsistency probblem)

ALTER TABLE exp.MaterialSource
   DROP CONSTRAINT UQ_MaterialSource_Name;



