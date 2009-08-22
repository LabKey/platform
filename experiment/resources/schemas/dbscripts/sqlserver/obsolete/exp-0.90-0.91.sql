/*
 * Copyright (c) 2005-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
CREATE VIEW exp.AllLsid AS
Select LSID, 'Protocol' AS Type From exp.Protocol UNION
Select LSID, 'ProtocolApplication' AS TYPE From exp.ProtocolApplication UNION
Select LSID, 'Experiment' AS TYPE From exp.Experiment UNION
Select LSID, 'Material' AS TYPE From exp.Material UNION
Select LSID, 'MaterialSource' AS TYPE From exp.MaterialSource UNION
Select LSID, 'Data' AS TYPE From exp.Data UNION
Select LSID, 'ExperimentRun' AS TYPE From exp.ExperimentRun
GO

CREATE VIEW exp.ExperimentRunDataOutputs AS
SELECT exp.Data.LSID AS DataLSID, exp.ExperimentRun.LSID AS RunLSID, exp.ExperimentRun.Container AS Container
FROM exp.Data
JOIN exp.ExperimentRun ON exp.Data.RunId=exp.ExperimentRun.RowId
WHERE SourceApplicationId IS NOT NULL
GO


CREATE VIEW exp.ExperimentRunMaterialInputs AS
SELECT exp.ExperimentRun.LSID AS RunLSID, exp.Material.*
FROM exp.ExperimentRun
JOIN exp.ProtocolApplication PAExp ON exp.ExperimentRun.RowId=PAExp.RunId
JOIN exp.MaterialInput ON exp.MaterialInput.TargetApplicationId=PAExp.RowId
JOIN exp.Material ON exp.MaterialInput.MaterialId=exp.Material.RowId
WHERE PAExp.CpasType='ExperimentRun'
GO

CREATE VIEW exp.ExperimentRunDataInputs AS
SELECT exp.ExperimentRun.LSID AS RunLSID, exp.Data.*
FROM exp.ExperimentRun
JOIN exp.ProtocolApplication PAExp ON exp.ExperimentRun.RowId=PAExp.RunId
JOIN exp.DataInput ON exp.DataInput.TargetApplicationId=PAExp.RowId
JOIN exp.Data ON exp.DataInput.DataId=exp.Data.RowId
WHERE PAExp.CpasType='ExperimentRun'
GO