ALTER TABLE exp.ProtocolApplication ADD EntityId ENTITYID;

EXEC core.executeJavaUpgradeCode 'generateExpProtocolApplicationEntityIds';

ALTER TABLE exp.ProtocolApplication ALTER COLUMN EntityId ENTITYID NOT NULL;
