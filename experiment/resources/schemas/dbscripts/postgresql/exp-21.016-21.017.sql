ALTER TABLE exp.Protocol ADD COLUMN Status VARCHAR(60);
UPDATE exp.Protocol SET Status = 'Active' WHERE ApplicationType = 'ExperimentRun';
