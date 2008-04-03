UPDATE study.Study SET DateBased=false where DateBased IS NULL;

ALTER TABLE study.ParticipantVisit
ADD Day int4;

ALTER TABLE study.Participant
ADD StartDate TIMESTAMP;