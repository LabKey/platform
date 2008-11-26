ALTER TABLE study.SpecimenEvent
	ADD COLUMN SpecimenNumber VARCHAR(50);

UPDATE study.SpecimenEvent SET SpecimenNumber =
	(SELECT SpecimenNumber FROM study.Specimen WHERE study.SpecimenEvent.SpecimenId = study.Specimen.RowId);

ALTER TABLE study.Specimen
	DROP COLUMN SpecimenNumber;