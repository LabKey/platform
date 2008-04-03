ALTER TABLE study.Study ADD
    DateBased BIT DEFAULT 0,
    StartDate DATETIME
  go

UPDATE study.Study SET DateBased=0 where DateBased is NULL
go

ALTER TABLE study.ParticipantVisit ADD
    Day INTEGER
  go

ALTER TABLE study.Participant ADD
    StartDate DATETIME
go

ALTER TABLE study.Dataset
ADD DemographicData BIT
go

UPDATE study.Dataset SET DemographicData=0 where DemographicData IS NULL
go

ALTER TABLE study.Dataset
ADD CONSTRAINT DF_DemographicData_False
DEFAULT 0 FOR DemographicData
go

ALTER TABLE study.Study ADD StudySecurity BIT DEFAULT 0
Go

UPDATE study.Study SET StudySecurity=1 where StudySecurity is NULL
Go