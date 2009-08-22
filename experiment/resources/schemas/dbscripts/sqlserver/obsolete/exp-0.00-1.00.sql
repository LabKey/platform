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

/*  Creates experiment annotation tables in the exp schema base on FuGE-OM types 
*  if exp objects already exist, need to run exp_drop.sql before
*  recreating them.
*
*  Author Peter Hussey
*  LabKey Software
*
*/

EXEC sp_addapprole 'exp', 'password'
GO


if NOT EXISTS (SELECT * FROM systypes WHERE name ='LSIDtype')
    EXEC sp_addtype 'LSIDtype', 'nvarchar(300)'
GO


CREATE TABLE exp.ExperimentRun (
	RowId int IDENTITY (1, 1) NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name nvarchar (50)  NULL ,
	ProtocolLSID LSIDtype NOT NULL ,
	ExperimentLSID LSIDtype NULL ,
	Comments ntext  NULL ,
	EntityId uniqueidentifier NULL ,
	Created datetime NULL ,
	CreatedBy int NULL ,
	Modified datetime NULL ,
	ModifiedBy int NULL ,
	Container uniqueidentifier NULL ,
	FilePathRoot nvarchar(500)
) 
GO

CREATE TABLE exp.Data (
	RowId int IDENTITY (1, 1) NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name nvarchar (200)    NULL ,
	CpasType nvarchar (50)  NULL ,
	SourceApplicationId int NULL ,
	SourceProtocolLSID LSIDtype NULL ,
	DataFileUrl nvarchar (400)  NULL ,
	RunId int NULL ,
	Created datetime NOT NULL, 
	Container uniqueidentifier NULL
) 
GO


CREATE TABLE exp.DataInput (
	DataId int NOT NULL ,
	TargetApplicationId int NOT NULL 
) 
GO

CREATE TABLE exp.Experiment (
	RowId int IDENTITY (1, 1) NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name nvarchar (200)    NULL ,
	Hypothesis nvarchar (500)  NULL ,
	ContactId nvarchar (100)  NULL ,
		ExperimentDescriptionURL nvarchar (200)  NULL ,
	Comments nvarchar (2000)  NULL ,
	EntityId uniqueidentifier NULL ,
	Created datetime NULL ,
	CreatedBy int NULL ,
	Modified datetime NULL ,
	ModifiedBy int NULL ,
	Container uniqueidentifier NULL 
) 
GO

CREATE TABLE exp.Fraction (
	MaterialId int NOT NULL ,
	StartPoint float NULL ,
	EndPoint float NULL ,
	ProteinAssay float NULL
)
GO

CREATE TABLE exp.Material (
	RowId int IDENTITY (1, 1) NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name nvarchar (200)    NULL ,
	CpasType nvarchar (200)  NULL ,
	SourceApplicationId int NULL ,
	SourceProtocolLSID LSIDtype NULL ,
	RunId int NULL ,
	Created datetime NOT NULL, 
	Container uniqueidentifier NULL,
) 
GO


CREATE TABLE exp.MaterialInput (
	MaterialId int NOT NULL ,
	TargetApplicationId int NOT NULL 
) 
GO

CREATE TABLE exp.BioSource (
	MaterialId int NOT NULL ,
	Individual nvarchar(50) NULL ,
	SampleOriginDate datetime NULL 
) 
GO


CREATE TABLE exp.Property (
	RowId int IDENTITY (1, 1) NOT NULL,
	ParentURI nvarchar(200) NOT NULL ,
	OntologyEntryURI nvarchar (200)  NOT NULL ,
	Name nvarchar(50) NULL ,
	ValueType nvarchar(50) NULL,
	StringValue nvarchar (400)  NULL ,
	PropertyURIValue nvarchar(200)  NULL ,
	IntegerValue int NULL ,
	DoubleValue float NULL ,
	DateTimeValue datetime NULL ,
	FileLinkValue nvarchar(400) NULL ,	
	XmlTextValue ntext NULL ,	
	RunId int NULL,
	ProcessDirective int NOT NULL DEFAULT 0
	)
GO


CREATE TABLE exp.PropertyDescriptor (
	RowId int IDENTITY (1, 1) NOT NULL,
	PropertyURI nvarchar(200) NOT NULL,
	OntologyURI nvarchar (200)  NULL,
	TypeURI nvarchar(200) NULL,
	Name nvarchar(50) NULL,
	Description ntext NULL,
	ValueType nvarchar(50) NULL
	)
GO


ALTER TABLE exp.PropertyDescriptor
   ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (RowId)
GO


ALTER TABLE exp.PropertyDescriptor
   ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI)
GO


CREATE TABLE exp.MaterialSource (
	RowId int IDENTITY (1, 1) NOT NULL,
	Name nvarchar(50) NOT NULL ,
    LSID LSIDtype NOT NULL,
	MaterialLSIDPrefix nvarchar(200) NULL,
	URLPattern nvarchar(200) NULL,
	Description ntext NULL ,
	Created datetime NULL ,
	CreatedBy int NULL ,
	Modified datetime NULL ,
	ModifiedBy int NULL ,
	Container uniqueidentifier NULL
    )
GO

ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT PK_MaterialSource PRIMARY KEY (RowId)
GO
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_Name UNIQUE (Name)
GO
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_LSID UNIQUE (LSID)
GO


CREATE TABLE exp.Protocol (
	RowId int IDENTITY (1, 1) NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name nvarchar (200)    NULL ,
	ProtocolDescription ntext  NULL ,
	ApplicationType nvarchar (50)  NULL ,
	MaxInputMaterialPerInstance int NULL ,
	MaxInputDataPerInstance int NULL ,
	OutputMaterialPerInstance int NULL ,
	OutputDataPerInstance int NULL ,
	OutputMaterialType nvarchar (50) NULL ,
	OutputDataType nvarchar (50) NULL ,
	Instrument nvarchar (200)  NULL ,
	Software nvarchar (200)  NULL ,
	ContactId nvarchar (100)  NULL ,
	Created datetime NULL ,
	EntityId uniqueidentifier NULL ,
	CreatedBy int NULL ,
	Modified datetime NULL ,
	ModifiedBy int NULL ,
	Container uniqueidentifier NULL 
) 
GO


CREATE TABLE exp.ProtocolAction (
	RowId int IDENTITY (10, 10) NOT NULL ,
	ParentProtocolId int NOT NULL ,
	ChildProtocolId int NOT NULL ,
	Sequence int NOT NULL
) 
GO


CREATE TABLE exp.ProtocolActionPredecessor (
	ActionId int NOT NULL ,
	PredecessorId int NOT NULL 
) 
GO


CREATE TABLE exp.ProtocolApplication (
	RowId int IDENTITY (1, 1) NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name nvarchar (200)    NULL ,
	CpasType nvarchar (50)  NULL ,
	ProtocolLSID LSIDtype NOT NULL ,
	ActivityDate datetime NULL ,
	Comments nvarchar (2000)  NULL ,
	RunId int NOT NULL ,
	ActionSequence int NOT NULL
) 
GO

CREATE TABLE exp.ProtocolParameter (
	RowId int IDENTITY (1, 1) NOT NULL,
	ProtocolId int NOT NULL ,
	Name nvarchar (200)   NULL ,
	ValueType nvarchar(50) NULL,
	StringValue nvarchar (400)  NULL ,
	IntegerValue int NULL ,
	DoubleValue float NULL ,
	DateTimeValue datetime NULL ,
	FileLinkValue nvarchar(400) NULL ,	
	XmlTextValue ntext NULL ,	
	OntologyEntryURI nvarchar (200)  NULL
) 
GO

CREATE TABLE exp.ProtocolApplicationParameter (
	RowId int IDENTITY (1, 1) NOT NULL,
	ProtocolApplicationId int NOT NULL ,
	Name nvarchar (200)   NULL ,
	ValueType nvarchar(50) NULL,
	StringValue nvarchar (400)  NULL ,
	IntegerValue int NULL ,
	DoubleValue float NULL ,
	DateTimeValue datetime NULL ,
	FileLinkValue nvarchar(400) NULL ,	
	XmlTextValue ntext NULL ,	
	OntologyEntryURI nvarchar (200)  NULL
) 
GO


ALTER TABLE exp.ExperimentRun 
	ADD CONSTRAINT PK_ExperimentRun PRIMARY KEY NONCLUSTERED (RowId)   
GO
ALTER TABLE exp.ExperimentRun 
	ADD CONSTRAINT UQ_ExperimentRun_LSID UNIQUE (LSID)   
GO

ALTER TABLE exp.Data 
	ADD CONSTRAINT PK_Data PRIMARY KEY NONCLUSTERED	(RowId)
GO
ALTER TABLE exp.Data 
	ADD CONSTRAINT UQ_Data_LSID UNIQUE (LSID)   
GO

ALTER TABLE exp.DataInput 
	ADD CONSTRAINT PK_DataInput PRIMARY KEY (DataId,TargetApplicationId)   
GO

ALTER TABLE exp.Experiment 
	ADD CONSTRAINT PK_Experiment PRIMARY KEY (RowId)
GO

ALTER TABLE exp.Experiment 
	ADD CONSTRAINT UQ_Experiment_LSID UNIQUE (LSID)   
GO

ALTER TABLE exp.Fraction 
	ADD CONSTRAINT PK_Fraction PRIMARY KEY(	MaterialId)
GO

ALTER TABLE exp.Material 
	ADD CONSTRAINT PK_Material PRIMARY KEY NONCLUSTERED (RowId) 
GO
ALTER TABLE exp.Material 
	ADD CONSTRAINT UQ_Material_LSID UNIQUE (LSID)   
GO

ALTER TABLE exp.BioSource 
	ADD CONSTRAINT PK_BioSource PRIMARY KEY  (MaterialId)   
GO

ALTER TABLE exp.MaterialInput 
	ADD CONSTRAINT PK_MaterialInput PRIMARY KEY  (MaterialId, TargetApplicationId)   
GO

ALTER TABLE exp.Property 
	ADD CONSTRAINT PK_Property PRIMARY KEY NONCLUSTERED  (RowId)   
GO
	
ALTER TABLE exp.Property 
	ADD CONSTRAINT UQ_Property_ParentURI_OntologyURI UNIQUE (ParentURI, OntologyEntryURI)   
GO

CREATE CLUSTERED INDEX IX_CL_Property_ParentURI ON exp.Property(ParentURI)
GO


ALTER TABLE exp.Protocol 
	ADD CONSTRAINT PK_Protocol PRIMARY KEY  (RowId)
GO

ALTER TABLE exp.Protocol 
	ADD CONSTRAINT UQ_Protocol_LSID UNIQUE (LSID)   
GO

ALTER TABLE exp.ProtocolAction 
	ADD CONSTRAINT PK_ProtocolAction PRIMARY KEY  (RowId)
GO
ALTER TABLE exp.ProtocolAction 
	ADD CONSTRAINT UQ_ProtocolAction UNIQUE (ParentProtocolId, ChildProtocolId, Sequence)   
GO

ALTER TABLE exp.ProtocolActionPredecessor 
	ADD CONSTRAINT PK_ActionPredecessor PRIMARY KEY  (ActionId,PredecessorId)   
GO

ALTER TABLE exp.ProtocolApplication 
	ADD CONSTRAINT PK_ProtocolApplication PRIMARY KEY NONCLUSTERED  (RowId)
GO

ALTER TABLE exp.ProtocolApplication 
	ADD CONSTRAINT UQ_ProtocolApp_LSID UNIQUE (LSID)   
GO

ALTER TABLE exp.ProtocolParameter 
	ADD CONSTRAINT PK_ProtocolParameter PRIMARY KEY (RowId) 
GO

ALTER TABLE exp.ProtocolParameter 
	ADD CONSTRAINT UQ_ProtocolParameter_Ord UNIQUE (ProtocolId,Name) 
GO

ALTER TABLE exp.ProtocolApplicationParameter 
	ADD CONSTRAINT PK_ProtocolAppParam PRIMARY KEY (RowId)
GO

ALTER TABLE exp.ProtocolApplicationParameter 
	ADD CONSTRAINT UQ_ProtocolAppParam_Ord UNIQUE (	ProtocolApplicationId,Name)
GO

ALTER TABLE exp.ExperimentRun ADD 
	CONSTRAINT FK_ExperimentRun_Experiment FOREIGN KEY (ExperimentLSID) 
		REFERENCES exp.Experiment (LSID)
GO

ALTER TABLE exp.ExperimentRun ADD 
	CONSTRAINT FK_ExperimentRun_Protocol FOREIGN KEY
	(
		ProtocolLSID
	) REFERENCES exp.Protocol (
		LSID
	)
GO

CREATE INDEX IX_CL_ExperimentRun_ExperimentLSID ON exp.ExperimentRun(ExperimentLSID)
GO


ALTER TABLE exp.Data ADD
	CONSTRAINT FK_Data_ExperimentRun FOREIGN KEY
	(
		RunId
	) REFERENCES exp.ExperimentRun (
		RowId
	)
GO

ALTER TABLE exp.Data ADD
	CONSTRAINT FK_Data_ProtocolApplication FOREIGN KEY 
	(
		SourceApplicationID
	) REFERENCES exp.ProtocolApplication (
		RowId
	)
GO

CREATE CLUSTERED INDEX IX_CL_Data_RunId ON exp.Data(RunId)
GO


ALTER TABLE exp.DataInput ADD 
	CONSTRAINT FK_DataInputData_Data FOREIGN KEY 
	(
		DataId
	) REFERENCES exp.Data (
		RowId
	)
GO
ALTER TABLE exp.DataInput ADD 
	CONSTRAINT FK_DataInput_ProtocolApplication FOREIGN KEY 
	(
		TargetApplicationId
	) REFERENCES exp.ProtocolApplication (
		RowId
	)
GO

CREATE INDEX IX_DataInput_TargetApplicationId ON exp.DataInput(TargetApplicationId)
GO


ALTER TABLE exp.Fraction ADD
	CONSTRAINT FK_Fraction_Material FOREIGN KEY
	(
		MaterialId
	) REFERENCES exp.Material (
		RowId
	)
GO

ALTER TABLE exp.Material ADD 
	CONSTRAINT FK_Material_ExperimentRun FOREIGN KEY
	(
		RunId
	) REFERENCES exp.ExperimentRun (
		RowId
	)
GO
ALTER TABLE exp.Material ADD 
	CONSTRAINT FK_Material_ProtocolApplication FOREIGN KEY 
	(
		SourceApplicationID
	) REFERENCES exp.ProtocolApplication (
		RowId
	)
GO

CREATE CLUSTERED INDEX IX_CL_Material_RunId ON exp.Material(RunId)
GO


ALTER TABLE exp.BioSource ADD 
	CONSTRAINT FK_BioSource_Material FOREIGN KEY 
	(
		MaterialId
	) REFERENCES exp.Material (
		RowId
	)
GO

ALTER TABLE exp.MaterialInput ADD 
	CONSTRAINT FK_MaterialInput_Material FOREIGN KEY 
	(
		MaterialId
	) REFERENCES exp.Material (
		RowId
	)
GO

ALTER TABLE exp.MaterialInput ADD 
	CONSTRAINT FK_MaterialInput_ProtocolApplication FOREIGN KEY 
	(
		TargetApplicationId
	) REFERENCES exp.ProtocolApplication (
		RowId
	)
GO

CREATE INDEX IX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId)
GO

ALTER TABLE exp.ProtocolAction ADD 
	CONSTRAINT FK_ProtocolAction_Parent_Protocol FOREIGN KEY 
	(
		ParentProtocolId 
	) REFERENCES exp.Protocol (
		RowId
	)
GO

ALTER TABLE exp.ProtocolAction ADD 
	CONSTRAINT FK_ProtocolAction_Child_Protocol FOREIGN KEY 
	(
		ChildProtocolId 
	) REFERENCES exp.Protocol (
		RowId
	)
GO

ALTER TABLE exp.ProtocolActionPredecessor ADD 
	CONSTRAINT FK_ActionPredecessor_Action_ProtocolAction FOREIGN KEY 
	(
		ActionId
	) REFERENCES exp.ProtocolAction (
		RowId
	)
GO

ALTER TABLE exp.ProtocolActionPredecessor ADD 
	CONSTRAINT FK_ActionPredecessor_Predecessor_ProtocolAction FOREIGN KEY 
	(
		PredecessorId
	) REFERENCES exp.ProtocolAction (
		RowId
	)
GO

ALTER TABLE exp.ProtocolApplication ADD 
	CONSTRAINT FK_ProtocolApplication_ExperimentRun FOREIGN KEY
	(
		RunId
	) REFERENCES exp.ExperimentRun (
		RowId
	)
GO

ALTER TABLE exp.ProtocolApplication ADD 
	CONSTRAINT FK_ProtocolApplication_Protocol FOREIGN KEY 
	(
		ProtocolLSID
	) REFERENCES exp.Protocol (
		LSID
	)
GO

CREATE CLUSTERED INDEX IX_CL_ProtocolApplication_RunId ON exp.ProtocolApplication(RunId)
GO

CREATE INDEX IX_ProtocolApplication_ProtocolLSID ON exp.ProtocolApplication(ProtocolLSID)
GO


ALTER TABLE exp.ProtocolParameter ADD 
	CONSTRAINT FK_ProtocolParameter_Protocol FOREIGN KEY 
	(
		ProtocolId
	) REFERENCES exp.Protocol (
		RowId
	)
GO


ALTER TABLE exp.ProtocolApplicationParameter ADD 
	CONSTRAINT FK_ProtocolAppParam_ProtocolApp FOREIGN KEY 
	(
		ProtocolApplicationId
	) REFERENCES exp.ProtocolApplication (
		RowId
	)
GO


