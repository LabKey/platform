CREATE VIEW exp.AllLsid AS
Select LSID, 'Protocol' AS Type From exp.Protocol UNION
Select LSID, 'ProtocolApplication' AS TYPE From exp.ProtocolApplication UNION
Select LSID, 'Experiment' AS TYPE From exp.Experiment UNION
Select LSID, 'Material' AS TYPE From exp.Material UNION
Select LSID, 'MaterialSource' AS TYPE From exp.MaterialSource UNION
Select LSID, 'Data' AS TYPE From exp.Data UNION
Select LSID, 'ExperimentRun' AS TYPE From exp.ExperimentRun;

CREATE VIEW exp.ExperimentRunDataOutputs AS
SELECT exp.Data.LSID AS DataLSID, exp.ExperimentRun.LSID AS RunLSID, exp.ExperimentRun.Container AS Container
FROM exp.Data
JOIN exp.ExperimentRun ON exp.Data.RunId=exp.ExperimentRun.RowId
WHERE SourceApplicationId IS NOT NULL;


CREATE VIEW exp.ExperimentRunMaterialInputs AS
SELECT exp.ExperimentRun.LSID AS RunLSID, exp.Material.*
FROM exp.ExperimentRun
JOIN exp.ProtocolApplication PAExp ON exp.ExperimentRun.RowId=PAExp.RunId
JOIN exp.MaterialInput ON exp.MaterialInput.TargetApplicationId=PAExp.RowId
JOIN exp.Material ON exp.MaterialInput.MaterialId=exp.Material.RowId
WHERE PAExp.CpasType='ExperimentRun';

CREATE VIEW exp.ExperimentRunDataInputs AS
SELECT exp.ExperimentRun.LSID AS RunLSID, exp.Data.*
FROM exp.ExperimentRun
JOIN exp.ProtocolApplication PAExp ON exp.ExperimentRun.RowId=PAExp.RunId
JOIN exp.DataInput ON exp.DataInput.TargetApplicationId=PAExp.RowId
JOIN exp.Data ON exp.DataInput.DataId=exp.Data.RowId
WHERE PAExp.CpasType='ExperimentRun';