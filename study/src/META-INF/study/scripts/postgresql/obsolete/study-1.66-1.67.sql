-- rename VisitDate -> _VisitDate to avoid some confusion

ALTER TABLE study.StudyData ADD _VisitDate TIMESTAMP NULL;

UPDATE study.StudyData SET _VisitDate = VisitDate;

ALTER TABLE study.StudyData DROP COLUMN VisitDate;
