
ALTER TABLE study.AssaySpecimen ADD Lab NVARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD SampleType NVARCHAR(200);
-- request from client to increase the size of the SampleType code field from 2 to 5 chars
ALTER TABLE study.StudyDesignSampleTypes ALTER COLUMN ShortSampleCode NVARCHAR(5);