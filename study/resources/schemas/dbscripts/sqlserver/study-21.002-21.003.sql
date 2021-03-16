EXEC sp_rename 'study.Study.DefaultAssayQCState', 'DefaultPublishDataQCState', 'COLUMN';

EXEC sp_rename 'study.Dataset.ProtocolId', 'PublishSourceId', 'COLUMN';

ALTER TABLE study.Dataset ADD PublishSourceType NVARCHAR(50);
GO
UPDATE study.Dataset SET PublishSourceType = 'Assay'
    WHERE PublishSourceId IS NOT NULL;
