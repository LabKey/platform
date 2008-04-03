ALTER TABLE study.Study
ADD DateBased Boolean DEFAULT false,
ADD StartDate TIMESTAMP;

UPDATE study.Study SET DateBased=false where DateBased IS NULL;

ALTER TABLE study.ParticipantVisit
ADD Day int4;

ALTER TABLE study.Participant
ADD StartDate TIMESTAMP;

ALTER TABLE study.Dataset
ADD DemographicData Boolean DEFAULT false;

UPDATE study.Dataset SET DemographicData=false where DemographicData IS NULL;

ALTER TABLE study.Study ADD StudySecurity Boolean DEFAULT false;

UPDATE study.Study SET StudySecurity=true;