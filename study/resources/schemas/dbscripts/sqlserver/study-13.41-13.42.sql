
-- Issue 19442: Change study.StudyDesignUnits “Name” field from 3 chars to 5 chars field length
ALTER TABLE study.StudyDesignUnits DROP CONSTRAINT pk_studydesignunits;
ALTER TABLE study.StudyDesignUnits ALTER COLUMN Name NVARCHAR(5) NOT NULL;
ALTER TABLE study.StudyDesignUnits ADD CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name);