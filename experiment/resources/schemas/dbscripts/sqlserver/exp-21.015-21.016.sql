ALTER TABLE exp.ExperimentRun ADD COLUMN WorkflowTask INT;
GO
ALTER TABLE exp.ExperimentRun ADD CONSTRAINT FK_Run_WorfklowTask FOREIGN KEY (WorkflowTask) REFERENCES exp.ProtocolApplication (RowId);
