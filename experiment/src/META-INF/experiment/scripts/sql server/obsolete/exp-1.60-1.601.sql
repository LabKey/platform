CREATE VIEW exp.ExperimentRunMaterialOutputs AS
	SELECT exp.Material.LSID AS MaterialLSID, exp.ExperimentRun.LSID AS RunLSID, exp.ExperimentRun.Container AS Container
	FROM exp.Material
	JOIN exp.ExperimentRun ON exp.Material.RunId=exp.ExperimentRun.RowId
	WHERE SourceApplicationId IS NOT NULL
GO
