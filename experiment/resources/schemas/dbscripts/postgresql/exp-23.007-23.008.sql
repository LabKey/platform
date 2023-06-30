ALTER TABLE exp.Material ADD COLUMN AvailableAliquotVolume FLOAT NULL;

SELECT core.executeJavaUpgradeCode('recomputeAliquotAvailableAmount');