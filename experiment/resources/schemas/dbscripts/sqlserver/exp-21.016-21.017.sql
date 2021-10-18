ALTER TABLE exp.Protocol ADD Status NVARCHAR(60);
GO
UPDATE exp.Protocol SET Status = 'Active' WHERE ApplicationType = 'ExperimentRun';
