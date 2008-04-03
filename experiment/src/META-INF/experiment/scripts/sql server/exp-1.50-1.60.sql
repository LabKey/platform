alter table exp.PropertyDomain ADD Required BIT NOT NULL,
CONSTRAINT DF_Required DEFAULT 0 FOR Required
go
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


DROP VIEW exp.ObjectPropertiesView
GO
CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.ObjectId, O.Container, O.ObjectURI, O.OwnerObjectId,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
go
