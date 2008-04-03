ALTER TABLE study.Dataset ADD Description NTEXT NULL
go

ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
go
ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
	(
		Container,
		DatasetId,
		SequenceNum,
		ParticipantId,
		_key
	)
go
