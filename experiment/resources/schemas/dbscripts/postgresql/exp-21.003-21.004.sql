ALTER TABLE exp.MaterialSource DROP COLUMN autolinktargetcontainerid;
ALTER TABLE exp.MaterialSource ADD COLUMN AutoLinkTargetContainerId VARCHAR(50) NULL;
