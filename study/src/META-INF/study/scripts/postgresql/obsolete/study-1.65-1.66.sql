ALTER TABLE study.DataSet ADD COLUMN KeyColumn VARCHAR(50);             -- Property name in TypeURI

ALTER TABLE study.StudyData ADD COLUMN _key VARCHAR(200) NULL;          -- assay key column, used only on INSERT for UQ index

ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;

ALTER TABLE study.studydata
    ADD CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key);
