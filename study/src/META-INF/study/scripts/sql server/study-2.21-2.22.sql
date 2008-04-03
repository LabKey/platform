UPDATE study.Study SET DateBased=0 where DateBased is NULL
go

ALTER TABLE study.ParticipantVisit ADD
    Day INTEGER
  go

ALTER TABLE study.Participant ADD
    StartDate DATETIME
go