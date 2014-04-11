ALTER TABLE study.StudySnapshot ADD ModifiedBy USERID;
ALTER TABLE study.StudySnapshot ADD Modified TIMESTAMP;

UPDATE study.StudySnapshot SET ModifiedBy = CreatedBy;
UPDATE study.StudySnapshot SET Modified = Created;
