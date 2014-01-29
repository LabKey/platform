
-- Issue 19442: Change study.StudyDesignUnits “Name” field from 3 chars to 5 chars field length
ALTER TABLE study.StudyDesignUnits ALTER COLUMN Name TYPE VARCHAR(5);