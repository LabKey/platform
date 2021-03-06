ALTER TABLE study.Study RENAME COLUMN DefaultAssayQCState TO DefaultPublishDataQCState;

ALTER TABLE study.Dataset RENAME COLUMN ProtocolId TO PublishSourceId;
ALTER TABLE study.Dataset ADD COLUMN PublishSourceType VARCHAR(50) NOT NULL DEFAULT 'Assay';
