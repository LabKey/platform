ALTER TABLE study.Dataset
ADD DemographicData Boolean DEFAULT false;

UPDATE study.Dataset SET DemographicData=false where DemographicData IS NULL;
