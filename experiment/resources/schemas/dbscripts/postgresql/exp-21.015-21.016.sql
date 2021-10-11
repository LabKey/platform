ALTER TABLE exp.ExperimentRun ADD COLUMN WorkflowTask INT;

ALTER TABLE exp.ExperimentRun ADD CONSTRAINT FK_Run_WorfklowTask FOREIGN KEY (WorkflowTask) REFERENCES exp.ProtocolApplication (RowId);
