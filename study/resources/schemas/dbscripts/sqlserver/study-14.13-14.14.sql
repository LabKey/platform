ALTER TABLE study.StudySnapshot ADD ModifiedBy USERID;
ALTER TABLE study.StudySnapshot ADD Modified DATETIME;
GO

UPDATE study.StudySnapshot SET ModifiedBy = CreatedBy;
UPDATE study.StudySnapshot SET Modified = Created;
GO
