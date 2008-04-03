CREATE TABLE exp.RunList (
	ExperimentId int not null,
	ExperimentRunId int not null,
	CONSTRAINT PK_RunList PRIMARY KEY (ExperimentId, ExperimentRunId),
	CONSTRAINT FK_RunList_ExperimentId FOREIGN KEY (ExperimentId)
			REFERENCES exp.Experiment(RowId),
	CONSTRAINT FK_RunList_ExperimentRunId FOREIGN KEY (ExperimentRunId)
			REFERENCES exp.ExperimentRun(RowId) )
go
INSERT INTO exp.RunList (ExperimentId, ExperimentRunId)
SELECT E.RowId, ER.RowId
   FROM exp.Experiment E INNER JOIN exp.ExperimentRun ER 
	ON (E.LSID = ER.ExperimentLSID)
go
ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_Experiment
GO
DROP INDEX exp.ExperimentRun.IX_CL_ExperimentRun_ExperimentLSID
GO
ALTER TABLE exp.ExperimentRun DROP COLUMN ExperimentLSID
GO




