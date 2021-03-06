sp_rename 'study.Study.DefaultAssayQCState', 'DefaultPublishDataQCState', 'COLUMN';

sp_rename 'study.Dataset.ProtocolId', 'PublishSourceId', 'COLUMN';
ALTER TABLE study.Dataset ADD PublishSourceType NVARCHAR(50) NOT NULL DEFAULT 'Assay';
