ALTER TABLE study.DataSet ADD KeyPropertyName NVARCHAR(50) NULL         -- Property name in TypeURI
go

ALTER TABLE study.StudyData ADD _key NVARCHAR(200) NULL                 -- assay key column, used only on INSERT for UQ index
go

ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;
go

ALTER TABLE study.StudyData
    ADD CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key);
go