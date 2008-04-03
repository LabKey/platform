-- ParticipantId --> VARCHAR()

DROP VIEW exp.StudyData;
ALTER TABLE exp.Data ALTER COLUMN StudyParticipantId TYPE VARCHAR(16);
CREATE VIEW exp.StudyData AS
    SELECT * FROM exp.Data WHERE StudyDatasetId IS NOT NULL;

-- index string/float properties

CREATE INDEX IDX_ObjectProperty_FloatValue ON exp.ObjectProperty (PropertyId, FloatValue);
CREATE INDEX IDX_ObjectProperty_StringValue ON exp.ObjectProperty (PropertyId, StringValue);
