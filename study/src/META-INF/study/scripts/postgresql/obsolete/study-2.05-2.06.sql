ALTER TABLE study.Dataset ADD COLUMN Description TEXT NULL
;

--ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
--ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
--	(
--		Container,
--		DatasetId,
--		SequenceNum,
--		ParticipantId,
--		_key
--	)
