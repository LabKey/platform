ALTER TABLE study.SpecimenEvent
	ADD SpecimenNumber NVARCHAR(50);
GO

UPDATE study.SpecimenEvent SET SpecimenNumber =
	(SELECT SpecimenNumber FROM study.Specimen WHERE study.SpecimenEvent.SpecimenId = study.Specimen.RowId);
GO

ALTER TABLE study.Specimen
	DROP COLUMN SpecimenNumber;
GO