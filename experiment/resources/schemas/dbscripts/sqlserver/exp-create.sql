/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
CREATE VIEW exp.ProtocolActionStepDetailsView AS
    SELECT     Protocol_P.LSID AS ParentProtocolLSID, Protocol_C.LSID AS LSID, Protocol_C.LSID AS ChildProtocolLSID, exp.ProtocolAction.Sequence AS Sequence, exp.ProtocolAction.Sequence AS ActionSequence,
                          exp.ProtocolAction.RowId AS ActionId, Protocol_C.RowId AS RowId, Protocol_C.Name AS Name,
                  Protocol_C.ProtocolDescription AS ProtocolDescription, Protocol_C.ApplicationType AS ApplicationType,
                          Protocol_C.MaxInputMaterialPerInstance AS MaxInputMaterialPerInstance, Protocol_C.MaxInputDataPerInstance AS MaxInputDataPerInstance,
                          Protocol_C.OutputMaterialPerInstance AS OutputMaterialPerInstance, Protocol_C.OutputDataPerInstance AS OutputDataPerInstance, Protocol_C.OutputMaterialType AS OutputMaterialType, Protocol_C.OutputDataType AS OutputDataType,
                          Protocol_C.Instrument AS Instrument, Protocol_C.Software AS Software, Protocol_C.contactId AS contactId,
                          Protocol_C.Created AS Created, Protocol_C.EntityId AS EntityId, Protocol_C.CreatedBy AS CreatedBy, Protocol_C.Modified AS Modified, Protocol_C.ModifiedBy AS ModifiedBy, Protocol_C.Container AS Container
    FROM         exp.Protocol Protocol_C INNER JOIN
                          exp.ProtocolAction ON Protocol_C.RowId = exp.ProtocolAction.ChildProtocolId INNER JOIN
                          exp.Protocol Protocol_P ON exp.ProtocolAction.ParentProtocolId = Protocol_P.RowId
GO

CREATE VIEW exp.ProtocolActionPredecessorLSIDView AS
    SELECT     Action.ParentProtocolLSID, Action.ChildProtocolLSID, Action.ActionSequence, PredecessorAction.ParentProtocolLSID AS PredecessorParentLSID,
                          PredecessorAction.ChildProtocolLSID AS PredecessorChildLSID, PredecessorAction.ActionSequence AS PredecessorSequence
    FROM         exp.ProtocolActionPredecessor INNER JOIN
                          exp.ProtocolActionStepDetailsView PredecessorAction ON exp.ProtocolActionPredecessor.PredecessorId = PredecessorAction.ActionId INNER JOIN
                          exp.ProtocolActionStepDetailsView Action ON exp.ProtocolActionPredecessor.ActionId = Action.ActionId
GO

CREATE VIEW exp.PredecessorOutputMaterialsView AS
    SELECT     exp.ProtocolActionPredecessorLSIDView.ParentProtocolLSID AS RunProtocolLSID,
                          exp.ProtocolActionPredecessorLSIDView.ChildProtocolLSID AS RunStepProtocolLSID,
                          exp.ProtocolActionPredecessorLSIDView.ActionSequence AS RunStepSequence, exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID,
                          exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID, exp.ProtocolActionPredecessorLSIDView.PredecessorSequence,
                          exp.Material.RowId AS OutputRowId, exp.Material.LSID AS OutputLSID, exp.Material.Name AS OutputName, exp.Material.CpasType AS OutputCpasType,
                          exp.ExperimentRun.RowId AS RunId
    FROM         exp.ProtocolApplication INNER JOIN
                          exp.ExperimentRun ON exp.ProtocolApplication.RunId = exp.ExperimentRun.RowId INNER JOIN
                          exp.ProtocolActionPredecessorLSIDView ON
                          exp.ProtocolApplication.ProtocolLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID AND
                          exp.ProtocolApplication.ActionSequence = exp.ProtocolActionPredecessorLSIDView.PredecessorSequence AND
                          exp.ExperimentRun.ProtocolLSID = exp.ProtocolActionPredecessorLSIDView.ParentProtocolLSID INNER JOIN
                          exp.Material ON exp.ProtocolApplication.RowId = exp.Material.SourceApplicationId
    WHERE     (exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID <> exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID)
GO

CREATE VIEW exp.PredecessorRunStartMaterialsView AS
SELECT     exp.ProtocolActionPredecessorLSIDView.ParentProtocolLSID AS RunProtocolLSID,
                      exp.ProtocolActionPredecessorLSIDView.ChildProtocolLSID AS RunStepProtocolLSID,
                      exp.ProtocolActionPredecessorLSIDView.ActionSequence AS RunStepSequence, exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID,
                      exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID, exp.ProtocolActionPredecessorLSIDView.PredecessorSequence, 
                      exp.Material.RowId AS OutputRowId, exp.Material.LSID AS OutputLSID, exp.Material.Name AS OutputName, exp.Material.CpasType AS OutputCpasType, 
                      exp.ExperimentRun.RowId AS RunId
FROM         exp.ProtocolApplication INNER JOIN
                      exp.ExperimentRun ON exp.ProtocolApplication.RunId = exp.ExperimentRun.RowId INNER JOIN
                      exp.ProtocolActionPredecessorLSIDView ON 
                      exp.ProtocolApplication.ProtocolLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID AND 
                      exp.ProtocolApplication.ActionSequence = exp.ProtocolActionPredecessorLSIDView.PredecessorSequence AND 
                      exp.ExperimentRun.ProtocolLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID INNER JOIN
                      exp.MaterialInput ON exp.ProtocolApplication.RowId = exp.MaterialInput.TargetApplicationId INNER JOIN
                      exp.Material ON exp.MaterialInput.MaterialId = exp.Material.RowId
WHERE     (exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID)
GO

CREATE VIEW exp.PredecessorAllMaterialsView AS
    SELECT     *
    FROM         exp.PredecessorRunStartMaterialsView
    UNION
    SELECT     *
    FROM         exp.PredecessorOutputMaterialsView
GO

CREATE VIEW exp.PredecessorOutputDataView AS
SELECT     exp.ProtocolActionPredecessorLSIDView.ParentProtocolLSID AS RunProtocolLSID,
                      exp.ProtocolActionPredecessorLSIDView.ChildProtocolLSID AS RunStepProtocolLSID,
                      exp.ProtocolActionPredecessorLSIDView.ActionSequence AS RunStepSequence, exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID,
                      exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID, exp.ProtocolActionPredecessorLSIDView.PredecessorSequence, 
                      exp.Data.RowId AS OutputRowId, exp.Data.LSID AS OutputLSID, exp.Data.Name AS OutputName, exp.Data.CpasType AS OutputCpasType, 
                      exp.ExperimentRun.RowId AS RunId
FROM         exp.ProtocolApplication INNER JOIN
                      exp.ExperimentRun ON exp.ProtocolApplication.RunId = exp.ExperimentRun.RowId INNER JOIN
                      exp.ProtocolActionPredecessorLSIDView ON 
                      exp.ProtocolApplication.ProtocolLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID AND 
                      exp.ProtocolApplication.ActionSequence = exp.ProtocolActionPredecessorLSIDView.PredecessorSequence AND 
                      exp.ExperimentRun.ProtocolLSID = exp.ProtocolActionPredecessorLSIDView.ParentProtocolLSID INNER JOIN
                      exp.Data ON exp.ProtocolApplication.RowId = exp.Data.SourceApplicationId
WHERE     (exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID <> exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID)
GO

CREATE VIEW exp.PredecessorRunStartDataView AS
    SELECT     exp.ProtocolActionPredecessorLSIDView.ParentProtocolLSID AS RunProtocolLSID,
                          exp.ProtocolActionPredecessorLSIDView.ChildProtocolLSID AS RunStepProtocolLSID,
                          exp.ProtocolActionPredecessorLSIDView.ActionSequence AS RunStepSequence, exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID,
                          exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID, exp.ProtocolActionPredecessorLSIDView.PredecessorSequence,
                          exp.Data.RowId AS OutputRowId, exp.Data.LSID AS OutputLSID, exp.Data.Name AS OutputName, exp.Data.CpasType AS OutputCpasType,
                          exp.ExperimentRun.RowId AS RunId
    FROM         exp.DataInput INNER JOIN
                          exp.ProtocolApplication INNER JOIN
                          exp.ExperimentRun ON exp.ProtocolApplication.RunId = exp.ExperimentRun.RowId INNER JOIN
                          exp.ProtocolActionPredecessorLSIDView ON
                          exp.ProtocolApplication.ProtocolLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID AND
                          exp.ProtocolApplication.ActionSequence = exp.ProtocolActionPredecessorLSIDView.PredecessorSequence AND
                          exp.ExperimentRun.ProtocolLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID ON
                          exp.DataInput.TargetApplicationId = exp.ProtocolApplication.RowId INNER JOIN
                          exp.Data ON exp.DataInput.DataId = exp.Data.RowId
    WHERE     (exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID)
GO

CREATE VIEW exp.PredecessorAllDataView AS
    SELECT     *
    FROM         exp.PredecessorRunStartDataView
    UNION
    SELECT     *
    FROM         exp.PredecessorOutputDataView
GO

CREATE VIEW exp.ChildMaterialForApplication AS

SELECT     exp.Material.RowId, exp.Material.LSID, exp.Material.Name, exp.Material.SourceApplicationId, exp.Material.RunId, exp.Material.Created,
                      exp.ProtocolApplication.RowId AS ApplicationID, exp.ProtocolApplication.LSID AS ApplicationLSID, exp.ProtocolApplication.Name AS ApplicationName, 
                      exp.ProtocolApplication.CpasType AS ApplicationType
FROM         exp.Material INNER JOIN
                      exp.ProtocolApplication ON exp.Material.SourceApplicationId = exp.ProtocolApplication.RowId
WHERE     (exp.ProtocolApplication.CpasType <> N'ExperimentRunOutput')

GO

CREATE VIEW exp.ChildDataForApplication AS

SELECT     exp.Data.RowId, exp.Data.LSID, exp.Data.Name, exp.Data.SourceApplicationId, exp.Data.DataFileUrl, exp.Data.RunId, exp.Data.Created,
                      exp.ProtocolApplication.RowId AS ApplicationID, exp.ProtocolApplication.LSID AS ApplicationLSID, exp.ProtocolApplication.Name AS ApplicationName, 
                      exp.ProtocolApplication.CpasType AS ApplicationType
FROM         exp.Data INNER JOIN
                      exp.ProtocolApplication ON exp.Data.SourceApplicationId = exp.ProtocolApplication.RowId
WHERE     (exp.ProtocolApplication.CpasType <> N'ExperimentRunOutput')

GO

CREATE VIEW exp.MarkedOutputMaterialForRun AS

    SELECT     exp.Material.RowId, exp.Material.LSID, exp.Material.Name, exp.Material.SourceApplicationId, exp.Material.RunId, exp.Material.Created,
                          PAStartNode.RowId AS ApplicationID, PAStartNode.LSID AS ApplicationLSID, PAStartNode.Name AS ApplicationName,
                          PAStartNode.CpasType AS ApplicationCpasType
    FROM         exp.Material INNER JOIN
                          exp.MaterialInput ON exp.Material.RowId = exp.MaterialInput.MaterialId INNER JOIN
                          exp.ProtocolApplication PAMarkOutputNode ON exp.MaterialInput.TargetApplicationId = PAMarkOutputNode.RowId INNER JOIN
                          exp.ProtocolApplication PAStartNode ON PAMarkOutputNode.RunId = PAStartNode.RunId
    WHERE     (PAMarkOutputNode.CpasType = N'ExperimentRunOutput') AND (PAStartNode.CpasType = N'ExperimentRun')

GO

CREATE VIEW exp.MarkedOutputDataForRun AS

    SELECT     exp.Data.RowId, exp.Data.LSID, exp.Data.Name, exp.Data.SourceApplicationId, exp.Data.DataFileUrl, exp.Data.RunId, exp.Data.Created,
                          PAStartNode.RowId AS ApplicationID, PAStartNode.LSID AS ApplicationLSID, PAStartNode.Name AS ApplicationName,
                          PAStartNode.CpasType AS ApplicationCpasType
    FROM         exp.Data INNER JOIN
                          exp.DataInput ON exp.Data.RowId = exp.DataInput.DataId INNER JOIN
                          exp.ProtocolApplication PAMarkOutputNode ON exp.DataInput.TargetApplicationId = PAMarkOutputNode.RowId INNER JOIN
                          exp.ProtocolApplication PAStartNode ON PAMarkOutputNode.RunId = PAStartNode.RunId
    WHERE     (PAMarkOutputNode.CpasType = N'ExperimentRunOutput') AND (PAStartNode.CpasType = N'ExperimentRun')
GO

CREATE VIEW exp.OutputMaterialForNode AS

    SELECT     RowId, LSID, Name, SourceApplicationId, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
    FROM         exp.ChildMaterialforApplication
    UNION ALL
    SELECT     RowId, LSID, Name, SourceApplicationId, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
    FROM         exp.MarkedOutputMaterialForRun
GO

CREATE VIEW exp.OutputDataForNode AS

    SELECT     RowId, LSID, Name, SourceApplicationId, DataFileUrl, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
    FROM         exp.ChildDataforApplication
    UNION ALL
    SELECT     RowId, LSID, Name, SourceApplicationId, DataFileUrl, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
    FROM         exp.MarkedOutputDataForRun
GO

CREATE VIEW exp.AllLsid AS
    SELECT LSID, 'Protocol' AS Type From exp.Protocol UNION
    SELECT LSID, 'ProtocolApplication' AS TYPE From exp.ProtocolApplication UNION
    SELECT LSID, 'Experiment' AS TYPE From exp.Experiment UNION
    SELECT LSID, 'Material' AS TYPE From exp.Material UNION
    SELECT LSID, 'MaterialSource' AS TYPE From exp.MaterialSource UNION
    SELECT LSID, 'Data' AS TYPE From exp.Data UNION
    SELECT LSID, 'ExperimentRun' AS TYPE From exp.ExperimentRun
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

CREATE VIEW exp.AllLsidContainers AS
    SELECT LSID, Container, 'Protocol' AS Type FROM exp.Protocol UNION ALL
    SELECT exp.ProtocolApplication.LSID, Container, 'ProtocolApplication' AS Type FROM exp.ProtocolApplication JOIN exp.Protocol ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID UNION ALL
    SELECT LSID, Container, 'Experiment' AS Type FROM exp.Experiment UNION ALL
    SELECT LSID, Container, 'Material' AS Type FROM exp.Material UNION ALL
    SELECT LSID, Container, 'MaterialSource' AS Type FROM exp.MaterialSource UNION ALL
    SELECT LSID, Container, 'Data' AS Type FROM exp.Data UNION ALL
    SELECT LSID, Container, 'ExperimentRun' AS Type FROM exp.ExperimentRun
GO

CREATE VIEW exp.ObjectClasses AS
    SELECT DomainURI
    FROM exp.DomainDescriptor
GO

CREATE VIEW exp.ExperimentRunMaterialOutputs AS
    SELECT exp.Material.LSID AS MaterialLSID, exp.ExperimentRun.LSID AS RunLSID, exp.ExperimentRun.Container AS Container
    FROM exp.Material
    JOIN exp.ExperimentRun ON exp.Material.RunId=exp.ExperimentRun.RowId
    WHERE SourceApplicationId IS NOT NULL
GO

CREATE VIEW exp.ObjectPropertiesView AS
    SELECT
        O.ObjectId, O.Container, O.ObjectURI, O.OwnerObjectId,
        PD.name, PD.PropertyURI, PD.RangeURI,
        P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.MvIndicator, PD.PropertyId, PD.ConceptURI, PD.Format
    FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId
        JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
GO
