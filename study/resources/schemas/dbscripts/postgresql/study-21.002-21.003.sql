ALTER TABLE study.Study RENAME COLUMN DefaultAssayQCState TO DefaultPublishDataQCState;

ALTER TABLE study.Dataset RENAME COLUMN ProtocolId TO PublishSourceId;
ALTER TABLE study.Dataset ADD COLUMN PublishSourceType VARCHAR(50);
UPDATE study.Dataset SET PublishSourceType = 'Assay'
    WHERE PublishSourceId IS NOT NULL;

