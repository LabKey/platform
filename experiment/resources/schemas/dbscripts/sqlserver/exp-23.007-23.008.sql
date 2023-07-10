ALTER TABLE exp.Material ADD AvailableAliquotCount INT NULL;
ALTER TABLE exp.Material ADD AvailableAliquotVolume FLOAT NULL;

EXEC core.executeJavaUpgradeCode 'recomputeAliquotAvailableAmount';