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


DROP VIEW exp.OutputMaterialForNode;
DROP VIEW exp.OutputDataForNode;
DROP VIEW exp.MarkedOutputMaterialForRun;
DROP VIEW exp.MarkedOutputDataForRun;
DROP VIEW exp.ChildMaterialForApplication;
DROP VIEW exp.ChildDataForApplication;
DROP VIEW exp.PredecessorAllDataView;
DROP VIEW exp.PredecessorRunStartDataView;
DROP VIEW exp.PredecessorOutputDataView;
DROP VIEW exp.PredecessorAllMaterialsView;
DROP VIEW exp.PredecessorRunStartMaterialsView;
DROP VIEW exp.PredecessorOutputMaterialsView;
DROP VIEW exp.ProtocolActionPredecessorLSIDView;
DROP VIEW exp.ProtocolActionStepDetailsView;
ALTER TABLE exp.Fraction DROP CONSTRAINT FK_Fraction_Material;
ALTER TABLE exp.Data DROP CONSTRAINT FK_Data_ExperimentRun;
ALTER TABLE exp.Material DROP CONSTRAINT FK_Material_ExperimentRun;
ALTER TABLE exp.ProtocolApplication DROP CONSTRAINT FK_ProtocolApplication_ExperimentRun;
ALTER TABLE exp.DataInput DROP CONSTRAINT FK_DataInputData_Data;
ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_Experiment;
ALTER TABLE exp.MaterialInput DROP CONSTRAINT FK_MaterialInput_Material;
ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_Protocol;
ALTER TABLE exp.ProtocolAction DROP CONSTRAINT FK_ProtocolAction_Parent_Protocol;
ALTER TABLE exp.ProtocolAction DROP CONSTRAINT FK_ProtocolAction_Child_Protocol;
ALTER TABLE exp.ProtocolApplication DROP CONSTRAINT FK_ProtocolApplication_Protocol;
ALTER TABLE exp.ProtocolApplicationParameter DROP CONSTRAINT FK_ProtocolAppParam_ProtocolApp;
ALTER TABLE exp.ProtocolParameter DROP CONSTRAINT FK_ProtocolParameter_Protocol;
ALTER TABLE exp.ProtocolActionPredecessor DROP CONSTRAINT FK_ActionPredecessor_Action_ProtocolAction;
ALTER TABLE exp.ProtocolActionPredecessor DROP CONSTRAINT FK_ActionPredecessor_Predecessor_ProtocolAction;
ALTER TABLE exp.Data DROP CONSTRAINT FK_Data_ProtocolApplication;
ALTER TABLE exp.DataInput DROP CONSTRAINT FK_DataInput_ProtocolApplication;
ALTER TABLE exp.Material DROP CONSTRAINT FK_Material_ProtocolApplication;
ALTER TABLE exp.MaterialInput DROP CONSTRAINT FK_MaterialInput_ProtocolApplication;
ALTER TABLE exp.BioSource DROP CONSTRAINT FK_BioSource_Material;

drop table exp.ExperimentRun;
drop table exp.BioSource;
drop table exp.Data;
drop table exp.DataInput;
drop table exp.Experiment;
drop table exp.Fraction;
drop table exp.Material;
drop table exp.MaterialInput;
drop table exp.Property;
drop table exp.Protocol;
drop table exp.ProtocolAction;
drop table exp.ProtocolActionPredecessor;
drop table exp.ProtocolApplication;
drop table exp.ProtocolParameter;
drop table exp.ProtocolApplicationParameter;
drop table exp.MaterialSource;
drop table exp.PropertyDescriptor;

DROP SCHEMA exp