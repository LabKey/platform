ALTER TABLE exp.Material ADD COLUMN AvailableAliquotCount INTEGER NULL;
ALTER TABLE exp.Material ADD COLUMN AvailableAliquotVolume FLOAT NULL;

SELECT core.executeJavaUpgradeCode('recomputeAliquotAvailableAmount');