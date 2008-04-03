ALTER TABLE study.StudyDesign
    ADD Active BIT
go

UPDATE study.StudyDesign SET Active=1 WHERE StudyEntityId IS NOT NULL
UPDATE study.StudyDesign SET Active=0 WHERE StudyEntityId IS NULL
go

ALTER TABLE study.StudyDesign
  ADD CONSTRAINT DF_Active DEFAULT 0 FOR Active
go

ALTER TABLE study.StudyDesign
    DROP COLUMN StudyEntityId
go

ALTER TABLE study.StudyDesign
    ADD SourceContainer ENTITYID
go

UPDATE study.StudyDesign SET SourceContainer = Container
go
