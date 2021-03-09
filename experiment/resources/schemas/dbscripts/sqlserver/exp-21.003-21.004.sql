ALTER TABLE exp.MaterialSource DROP COLUMN autolinktargetcontainerid;
ALTER TABLE exp.MaterialSource ADD COLUMN AutoLinkTargetContainerId NVARCHAR(50) NULL;
GO
