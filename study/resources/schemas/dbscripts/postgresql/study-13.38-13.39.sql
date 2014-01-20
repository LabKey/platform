
ALTER TABLE study.AssaySpecimen ADD COLUMN Lab VARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD COLUMN SampleType VARCHAR(200);
-- request from client to increase the size of the SampleType code field from 2 to 5 chars
ALTER TABLE study.StudyDesignSampleTypes ALTER COLUMN ShortSampleCode TYPE VARCHAR(5);