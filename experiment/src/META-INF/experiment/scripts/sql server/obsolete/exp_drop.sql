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

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[getObjectProperties]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.getObjectProperties
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ensureObject]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.ensureObject
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[deleteObject]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.deleteObject
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[setFloatProperties]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.setFloatProperties
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[setStringProperties]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.setStringProperties
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[setDateTimeProperties]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.setDateTimeProperties
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[_insertFloatProperty]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp._insertFloatProperty
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[_insertDateTimeProperty]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp._insertDateTimeProperty
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[_insertStringProperty]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp._insertStringProperty
GO


if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[OutputMaterialForNode]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.OutputMaterialForNode
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ExperimentRunMaterialInputs]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ExperimentRunMaterialInputs
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ExperimentRunDataInputs]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ExperimentRunDataInputs
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ExperimentRunDataOutputs]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ExperimentRunDataOutputs
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ObjectPropertiesView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ObjectPropertiesView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ObjectClasses]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ObjectClasses
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[AllLsid]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.AllLsid
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[AllLsidContainers]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.AllLsidContainers
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[OutputDataForNode]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.OutputDataForNode
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[MarkedOutputMaterialForRun]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.MarkedOutputMaterialForRun
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[MarkedOutputDataForRun]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.MarkedOutputDataForRun
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ChildMaterialForApplication]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ChildMaterialForApplication
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ChildDataForApplication]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ChildDataForApplication
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[PredecessorAllDataView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.PredecessorAllDataView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[PredecessorRunStartDataView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.PredecessorRunStartDataView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[PredecessorOutputDataView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.PredecessorOutputDataView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[PredecessorAllMaterialsView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.PredecessorAllMaterialsView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[PredecessorRunStartMaterialsView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.PredecessorRunStartMaterialsView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[PredecessorOutputMaterialsView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.PredecessorOutputMaterialsView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ProtocolActionPredecessorLSIDView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ProtocolActionPredecessorLSIDView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ProtocolActionStepDetailsView]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ProtocolActionStepDetailsView
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_Data_ExperimentRun]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[Data] DROP CONSTRAINT FK_Data_ExperimentRun
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_Material_ExperimentRun]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[Material] DROP CONSTRAINT FK_Material_ExperimentRun
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ProtocolApplication_ExperimentRun]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ProtocolApplication] DROP CONSTRAINT FK_ProtocolApplication_ExperimentRun
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_DataInputData_Data]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[DataInput] DROP CONSTRAINT FK_DataInputData_Data
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ExperimentRun_Experiment]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ExperimentRun] DROP CONSTRAINT FK_ExperimentRun_Experiment
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_MaterialInput_Material]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[MaterialInput] DROP CONSTRAINT FK_MaterialInput_Material
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ExperimentRun_Protocol]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ExperimentRun] DROP CONSTRAINT FK_ExperimentRun_Protocol
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_Object_Object ]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[Object] DROP CONSTRAINT FK_Object_Object
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ObjectProperty_Object]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ObjectProperty] DROP CONSTRAINT FK_ObjectProperty_Object
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ObjectProperty_PropertyDescriptor]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ObjectProperty] DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ProtocolAction_Parent_Protocol]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ProtocolAction] DROP CONSTRAINT FK_ProtocolAction_Parent_Protocol
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ProtocolAction_Child_Protocol]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ProtocolAction] DROP CONSTRAINT FK_ProtocolAction_Child_Protocol
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ProtocolApplication_Protocol]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ProtocolApplication] DROP CONSTRAINT FK_ProtocolApplication_Protocol
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ProtocolAppParam_ProtocolApp]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ProtocolApplicationParameter] DROP CONSTRAINT FK_ProtocolAppParam_ProtocolApp
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ProtocolParameter_Protocol]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ProtocolParameter] DROP CONSTRAINT FK_ProtocolParameter_Protocol
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ActionPredecessor_Action_ProtocolAction]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ProtocolActionPredecessor] DROP CONSTRAINT FK_ActionPredecessor_Action_ProtocolAction
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_ActionPredecessor_Predecessor_ProtocolAction]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[ProtocolActionPredecessor] DROP CONSTRAINT FK_ActionPredecessor_Predecessor_ProtocolAction
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_Data_ProtocolApplication]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[Data] DROP CONSTRAINT FK_Data_ProtocolApplication
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_DataInput_ProtocolApplication]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[DataInput] DROP CONSTRAINT FK_DataInput_ProtocolApplication
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_Material_ProtocolApplication]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[Material] DROP CONSTRAINT FK_Material_ProtocolApplication
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_MaterialInput_ProtocolApplication]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[MaterialInput] DROP CONSTRAINT FK_MaterialInput_ProtocolApplication
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[FK_ApplicationLSID_ProtocolApplication]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [dbo].[MS2Runs] DROP CONSTRAINT FK_ApplicationLSID_ProtocolApplication
GO


if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ExperimentRun]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[ExperimentRun]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[Data]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[Data]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[DataInput]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[DataInput]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[Experiment]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[Experiment]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[Material]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[Material]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[MaterialInput]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[MaterialInput]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[Object]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[Object]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ObjectProperty]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[ObjectProperty]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[PropertyDescriptor]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[PropertyDescriptor]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[Protocol]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[Protocol]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ProtocolAction]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[ProtocolAction]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ProtocolActionPredecessor]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[ProtocolActionPredecessor]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ProtocolApplication]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[ProtocolApplication]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ProtocolParameter]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[ProtocolParameter]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ProtocolApplicationParameter]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[ProtocolApplicationParameter]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[MaterialSource]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[MaterialSource]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[PropertyDescriptor]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[PropertyDescriptor]
GO

if EXISTS (select * from sysusers where name ='exp')
	exec sp_dropapprole 'exp'
GO
