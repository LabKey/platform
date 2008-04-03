ALTER TABLE study.Dataset
ADD DemographicData BIT
go

UPDATE study.Dataset SET DemographicData=0 where DemographicData IS NULL
go

ALTER TABLE study.Dataset
ADD CONSTRAINT DF_DemographicData_False
DEFAULT 0 FOR DemographicData
go



