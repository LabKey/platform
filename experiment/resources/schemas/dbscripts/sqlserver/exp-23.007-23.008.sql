ALTER TABLE exp.Material ADD AvailableAliquotVolume FLOAT NULL;

EXEC core.executeJavaUpgradeCode 'recomputeAliquotAvailableAmount';