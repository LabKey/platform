-- rename VisitDate -> _VisitDate to avoid some confusion
 
ALTER TABLE study.StudyData ADD _VisitDate DATETIME NULL
go
UPDATE study.StudyData SET _VisitDate = VisitDate
go
ALTER TABLE study.StudyData DROP COLUMN VisitDate
go