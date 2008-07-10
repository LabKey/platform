/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
exec core.fn_dropifexists 'Experiment', 'exp', 'INDEX', 'IX_Experiment_Container'
GO
exec core.fn_dropifexists 'ExperimentRun', 'exp', 'INDEX', 'IX_CL_ExperimentRun_Container'
GO
exec core.fn_dropifexists 'ExperimentRun', 'exp', 'INDEX', 'IX_ExperimentRun_ProtocolLSID'
GO
exec core.fn_dropifexists 'RunList', 'exp', 'INDEX', 'IX_RunList_ExperimentRunId'
GO
exec core.fn_dropifexists 'ProtocolApplicationParameter', 'exp', 'INDEX', 'IX_ProtocolApplicationParameter_AppId'
GO
exec core.fn_dropifexists 'ProtocolActionPredecessor', 'exp', 'INDEX', 'IX_ProtocolActionPredecessor_PredecessorId'
GO
exec core.fn_dropifexists 'ProtocolAction', 'exp', 'INDEX', 'IX_ProtocolAction_ChildProtocolId'
GO
exec core.fn_dropifexists 'ProtocolParameter', 'exp', 'INDEX', 'IX_ProtocolParameter_ProtocolId'
GO
exec core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_Container'
GO
exec core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_SourceApplicationId'
GO
exec core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_DataFileUrl'
GO
exec core.fn_dropifexists 'DataInput', 'exp', 'INDEX', 'IX_DataInput_PropertyId'
GO
exec core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_Container'
GO
exec core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_SourceApplicationId'
GO
exec core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_CpasType'
GO
exec core.fn_dropifexists 'MaterialInput', 'exp', 'INDEX', 'IX_MaterialInput_PropertyId'
GO
exec core.fn_dropifexists 'MaterialSource', 'exp', 'INDEX', 'IX_MaterialSource_Container'
GO
exec core.fn_dropifexists 'ActiveMaterialSource', 'exp', 'INDEX', 'IX_ActiveMaterialSource_MaterialSourceLSID'
GO
exec core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject'
GO
exec core.fn_dropifexists 'PropertyDomain', 'exp', 'INDEX', 'IX_PropertyDomain_DomainId'
GO
exec core.fn_dropifexists 'PropertyDescriptor', 'exp', 'INDEX', 'IX_PropertyDescriptor_Container'
GO
exec core.fn_dropifexists 'DomainDescriptor', 'exp', 'INDEX', 'IX_DomainDescriptor_Container'
GO
exec core.fn_dropifexists 'Object', 'exp', 'INDEX', 'IX_Object_OwnerObjectId'
GO
-- redundant to unique constraint
exec core.fn_dropifexists 'Object', 'exp', 'INDEX', 'IX_Object_Uri'
GO



CREATE INDEX IX_Experiment_Container ON exp.Experiment(Container)
GO
create clustered index IX_CL_ExperimentRun_Container ON exp.ExperimentRun(Container)
GO
create index IX_ExperimentRun_ProtocolLSID ON exp.ExperimentRun(ProtocolLSID)
GO
CREATE INDEX IX_RunList_ExperimentRunId ON exp.RunList(ExperimentRunId)
GO
CREATE INDEX IX_ProtocolApplicationParameter_AppId ON exp.ProtocolApplicationParameter(ProtocolApplicationId)
GO
CREATE INDEX IX_ProtocolActionPredecessor_PredecessorId on exp.ProtocolActionPredecessor(PredecessorId)
GO
CREATE INDEX IX_ProtocolAction_ChildProtocolId on exp.ProtocolAction(ChildProtocolId)
GO
CREATE INDEX IX_ProtocolParameter_ProtocolId ON exp.ProtocolParameter(ProtocolId)
GO

CREATE INDEX IX_Data_Container ON exp.Data(Container)
GO
CREATE INDEX IX_Data_SourceApplicationId ON exp.Data(SourceApplicationId)
GO
CREATE INDEX IX_DataInput_PropertyId ON exp.DataInput(PropertyId)
GO
CREATE INDEX IX_Data_DataFileUrl ON exp.Data(DataFileUrl)
GO

CREATE INDEX IX_Material_Container ON exp.Material(Container)
GO
CREATE INDEX IX_Material_SourceApplicationId ON exp.Material(SourceApplicationId)
GO
CREATE INDEX IX_MaterialInput_PropertyId ON exp.MaterialInput(PropertyId)
GO
CREATE INDEX IX_Material_CpasType ON exp.Material(CpasType)
GO


CREATE INDEX IX_MaterialSource_Container ON exp.MaterialSource(Container)
GO
CREATE INDEX IX_ActiveMaterialSource_MaterialSourceLSID on exp.ActiveMaterialSource(MaterialSourceLSID)
GO


CREATE  INDEX IX_ObjectProperty_PropertyObject ON exp.ObjectProperty(PropertyId, ObjectId)
GO
CREATE INDEX IX_PropertyDomain_DomainId ON exp.PropertyDomain(DomainID)
GO
CREATE INDEX IX_PropertyDescriptor_Container ON exp.PropertyDescriptor(Container)
GO
CREATE INDEX IX_DomainDescriptor_Container ON exp.DomainDescriptor(Container)
GO
CREATE INDEX IX_Object_OwnerObjectId ON exp.Object(OwnerObjectId)
GO
