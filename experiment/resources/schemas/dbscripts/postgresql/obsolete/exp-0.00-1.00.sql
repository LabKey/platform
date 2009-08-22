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

CREATE SCHEMA exp;
SET search_path TO exp, public;

CREATE DOMAIN public.LSIDType AS VARCHAR(300);


CREATE TABLE exp.ExperimentRun (
	RowId SERIAL NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name VARCHAR (50)  NULL ,
	ProtocolLSID LSIDtype NOT NULL ,
	ExperimentLSID LSIDtype NULL ,
	Comments TEXT  NULL ,
	EntityId ENTITYID NULL ,
	Created TIMESTAMP NULL ,
	CreatedBy int NULL ,
	Modified TIMESTAMP NULL ,
	ModifiedBy int NULL ,
	Container ENTITYID NULL ,
	FilePathRoot VARCHAR(500)
) ;

CREATE TABLE exp.Data (
	RowId SERIAL NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name VARCHAR (200)    NULL ,
	CpasType VARCHAR (50)  NULL ,
	SourceApplicationId int NULL ,
	SourceProtocolLSID LSIDtype NULL ,
	DataFileUrl VARCHAR (400)  NULL ,
	RunId int NULL ,
	Created TIMESTAMP NOT NULL,
	Container ENTITYID NULL
) ;

CREATE TABLE exp.DataInput (
	DataId int NOT NULL ,
	TargetApplicationId int NOT NULL
) ;

CREATE TABLE exp.Experiment (
	RowId SERIAL NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name VARCHAR (200)    NULL ,
	Hypothesis VARCHAR (500)  NULL ,
	ContactId VARCHAR (100)  NULL ,
	ExperimentDescriptionURL VARCHAR (200)  NULL ,
	Comments VARCHAR (2000)  NULL ,
	EntityId ENTITYID NULL ,
	Created TIMESTAMP NULL ,
	CreatedBy int NULL ,
	Modified TIMESTAMP NULL ,
	ModifiedBy int NULL ,
	Container ENTITYID NULL
) ;

CREATE TABLE exp.Fraction (
	MaterialId int NOT NULL ,
	StartPoint float NULL ,
	EndPoint float NULL ,
	ProteinAssay float NULL
);

CREATE TABLE exp.Material (
	RowId SERIAL NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name VARCHAR (200)    NULL ,
	CpasType VARCHAR (200)  NULL ,
	SourceApplicationId int NULL ,
	SourceProtocolLSID LSIDtype NULL ,
	RunId int NULL ,
	Created TIMESTAMP NOT NULL,
	Container ENTITYID NULL
) ;

CREATE TABLE exp.MaterialInput (
	MaterialId int NOT NULL ,
	TargetApplicationId int NOT NULL
) ;

CREATE TABLE exp.BioSource (
	MaterialId int NOT NULL ,
	Individual VARCHAR(50) NULL ,
	SampleOriginDate TIMESTAMP NULL
) ;


CREATE TABLE exp.Property (
	RowId SERIAL NOT NULL,
	ParentURI VARCHAR(200) NOT NULL ,
	OntologyEntryURI VARCHAR (200)  NOT NULL ,
	Name VARCHAR(50) NULL ,
	ValueType VARCHAR(50) NULL,
	StringValue VARCHAR (400)  NULL ,
	PropertyURIValue VARCHAR(200)  NULL ,
	IntegerValue int NULL ,
	DoubleValue float NULL ,
	DateTimeValue TIMESTAMP NULL ,
	FileLinkValue VARCHAR(400) NULL ,
	XmlTextValue TEXT NULL ,
	RunId int NULL,
	ProcessDirective int NOT NULL DEFAULT 0
	);

CREATE TABLE exp.PropertyDescriptor (
	RowId SERIAL NOT NULL,
	PropertyURI VARCHAR(200) NOT NULL,
	OntologyURI VARCHAR (200)  NULL,
	TypeURI VARCHAR(200) NULL,
	ValueType VARCHAR(50) NULL,
	Name VARCHAR(50) NULL ,
	Description TEXT NULL
	);

ALTER TABLE exp.PropertyDescriptor
   ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (RowId);
ALTER TABLE exp.PropertyDescriptor
   ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI);

CREATE TABLE exp.MaterialSource (
	RowId SERIAL NOT NULL,
	Name VARCHAR(50) NOT NULL ,
    LSID LSIDtype NOT NULL,
	MaterialLSIDPrefix VARCHAR(200) NULL,
	URLPattern VARCHAR(200) NULL,
	Description TEXT NULL ,
	Created TIMESTAMP NULL ,
	CreatedBy int NULL ,
	Modified TIMESTAMP NULL ,
	ModifiedBy int NULL ,
	Container ENTITYID NULL
    );

ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT PK_MaterialSource PRIMARY KEY (RowId);
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_Name UNIQUE (Name);
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_LSID UNIQUE (LSID);

CREATE TABLE exp.Protocol (
	RowId SERIAL NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name VARCHAR (200)    NULL ,
	ProtocolDescription TEXT  NULL ,
	ApplicationType VARCHAR (50)  NULL ,
	MaxInputMaterialPerInstance int NULL ,
	MaxInputDataPerInstance int NULL ,
	OutputMaterialPerInstance int NULL ,
	OutputDataPerInstance int NULL ,
	OutputMaterialType VARCHAR (50) NULL ,
	OutputDataType VARCHAR (50) NULL ,
	Instrument VARCHAR (200)  NULL ,
	Software VARCHAR (200)  NULL ,
	ContactId VARCHAR (100)  NULL ,
	Created TIMESTAMP NULL ,
	EntityId ENTITYID NULL ,
	CreatedBy int NULL ,
	Modified TIMESTAMP NULL ,
	ModifiedBy int NULL ,
	Container ENTITYID NULL
) ;

CREATE TABLE exp.ProtocolAction (
	RowId SERIAL NOT NULL ,
	ParentProtocolId int NOT NULL ,
	ChildProtocolId int NOT NULL ,
	Sequence int NOT NULL
) ;

CREATE TABLE exp.ProtocolActionPredecessor (
	ActionId int NOT NULL ,
	PredecessorId int NOT NULL
) ;

CREATE TABLE exp.ProtocolApplication (
	RowId SERIAL NOT NULL,
	LSID LSIDtype NOT NULL ,
	Name VARCHAR (200)    NULL ,
	CpasType VARCHAR (50)  NULL ,
	ProtocolLSID LSIDtype NOT NULL ,
	ActivityDate TIMESTAMP NULL ,
	Comments VARCHAR (2000)  NULL ,
	RunId int NOT NULL ,
	ActionSequence int NOT NULL
) ;

CREATE TABLE exp.ProtocolParameter (
	RowId SERIAL NOT NULL,
	ProtocolId int NOT NULL ,
	Name VARCHAR (200)   NULL ,
	ValueType VARCHAR(50) NULL,
	StringValue VARCHAR (400)  NULL ,
	IntegerValue int NULL ,
	DoubleValue float NULL ,
	DateTimeValue TIMESTAMP NULL ,
	FileLinkValue VARCHAR(400) NULL ,
	XmlTextValue TEXT NULL ,
	OntologyEntryURI VARCHAR (200)  NULL
) ;

CREATE TABLE exp.ProtocolApplicationParameter (
	RowId SERIAL NOT NULL,
	ProtocolApplicationId int NOT NULL ,
	Name VARCHAR (200)   NULL ,
	ValueType VARCHAR(50) NULL,
	StringValue VARCHAR (400)  NULL ,
	IntegerValue int NULL ,
	DoubleValue float NULL ,
	DateTimeValue TIMESTAMP NULL ,
	FileLinkValue VARCHAR(400) NULL ,
	XmlTextValue TEXT NULL ,
	OntologyEntryURI VARCHAR (200)  NULL
) ;


ALTER TABLE exp.ExperimentRun
	ADD CONSTRAINT PK_ExperimentRun PRIMARY KEY  (RowId)   ;
ALTER TABLE exp.ExperimentRun
	ADD CONSTRAINT UQ_ExperimentRun_LSID UNIQUE (LSID)   ;

ALTER TABLE exp.Data
	ADD CONSTRAINT PK_Data PRIMARY KEY 	(RowId);
ALTER TABLE exp.Data
	ADD CONSTRAINT UQ_Data_LSID UNIQUE (LSID)   ;

ALTER TABLE exp.DataInput
	ADD CONSTRAINT PK_DataInput PRIMARY KEY (DataId,TargetApplicationId)   ;

ALTER TABLE exp.Experiment
	ADD CONSTRAINT PK_Experiment PRIMARY KEY (RowId);

ALTER TABLE exp.Experiment
	ADD CONSTRAINT UQ_Experiment_LSID UNIQUE (LSID)   ;

ALTER TABLE exp.Fraction
	ADD CONSTRAINT PK_Fraction PRIMARY KEY(	MaterialId);

ALTER TABLE exp.Material
	ADD CONSTRAINT PK_Material PRIMARY KEY  (RowId) ;
ALTER TABLE exp.Material
	ADD CONSTRAINT UQ_Material_LSID UNIQUE (LSID)   ;

ALTER TABLE exp.BioSource
	ADD CONSTRAINT PK_BioSource PRIMARY KEY  (MaterialId)   ;

ALTER TABLE exp.MaterialInput
	ADD CONSTRAINT PK_MaterialInput PRIMARY KEY  (MaterialId, TargetApplicationId)   ;

ALTER TABLE exp.Property
	ADD CONSTRAINT PK_Property PRIMARY KEY   (RowId)   ;

ALTER TABLE exp.Property
	ADD CONSTRAINT UQ_Property_ParentURI_OntologyURI UNIQUE (ParentURI, OntologyEntryURI)   ;

CREATE INDEX IDX_CL_Property_ParentURI ON exp.Property(ParentURI);


ALTER TABLE exp.Protocol
	ADD CONSTRAINT PK_Protocol PRIMARY KEY  (RowId);

ALTER TABLE exp.Protocol
	ADD CONSTRAINT UQ_Protocol_LSID UNIQUE (LSID)   ;

ALTER TABLE exp.ProtocolAction
	ADD CONSTRAINT PK_ProtocolAction PRIMARY KEY  (RowId);
ALTER TABLE exp.ProtocolAction
	ADD CONSTRAINT UQ_ProtocolAction UNIQUE (ParentProtocolId, ChildProtocolId, Sequence)   ;

ALTER TABLE exp.ProtocolActionPredecessor
	ADD CONSTRAINT PK_ActionPredecessor PRIMARY KEY  (ActionId,PredecessorId)   ;

ALTER TABLE exp.ProtocolApplication
	ADD CONSTRAINT PK_ProtocolApplication PRIMARY KEY   (RowId)   ;

ALTER TABLE exp.ProtocolApplication
	ADD CONSTRAINT UQ_ProtocolApp_LSID UNIQUE (LSID)   ;

ALTER TABLE exp.ProtocolParameter
	ADD CONSTRAINT PK_ProtocolParameter PRIMARY KEY (RowId) ;

ALTER TABLE exp.ProtocolParameter
	ADD CONSTRAINT UQ_ProtocolParameter_Ord UNIQUE (ProtocolId,Name) ;

ALTER TABLE exp.ProtocolApplicationParameter
	ADD CONSTRAINT PK_ProtocolAppParam PRIMARY KEY (RowId);

ALTER TABLE exp.ProtocolApplicationParameter
	ADD CONSTRAINT UQ_ProtocolAppParam_Ord UNIQUE (	ProtocolApplicationId,Name);

ALTER TABLE exp.ExperimentRun ADD
	CONSTRAINT FK_ExperimentRun_Experiment FOREIGN KEY (ExperimentLSID)
		REFERENCES exp.Experiment (LSID);

ALTER TABLE exp.ExperimentRun ADD
	CONSTRAINT FK_ExperimentRun_Protocol FOREIGN KEY
	(
		ProtocolLSID
	) REFERENCES exp.Protocol (
		LSID
	);

CREATE INDEX IDX_CL_ExperimentRun_ExperimentLSID ON exp.ExperimentRun(ExperimentLSID);


ALTER TABLE exp.Data ADD
	CONSTRAINT FK_Data_ExperimentRun FOREIGN KEY
	(
		RunId
	) REFERENCES exp.ExperimentRun (
		RowId
	);

ALTER TABLE exp.Data ADD
	CONSTRAINT FK_Data_ProtocolApplication FOREIGN KEY
	(
		SourceApplicationID
	) REFERENCES exp.ProtocolApplication (
		RowId
	);

CREATE INDEX IDX_CL_Data_RunId ON exp.Data(RunId);


ALTER TABLE exp.DataInput ADD
	CONSTRAINT FK_DataInputData_Data FOREIGN KEY
	(
		DataId
	) REFERENCES exp.Data (
		RowId
	);
ALTER TABLE exp.DataInput ADD
	CONSTRAINT FK_DataInput_ProtocolApplication FOREIGN KEY
	(
		TargetApplicationId
	) REFERENCES exp.ProtocolApplication (
		RowId
	);

CREATE INDEX IDX_DataInput_TargetApplicationId ON exp.DataInput(TargetApplicationId);


ALTER TABLE exp.Fraction ADD
	CONSTRAINT FK_Fraction_Material FOREIGN KEY
	(
		MaterialId
	) REFERENCES exp.Material (
		RowId
	);

ALTER TABLE exp.Material ADD
	CONSTRAINT FK_Material_ExperimentRun FOREIGN KEY
	(
		RunId
	) REFERENCES exp.ExperimentRun (
		RowId
	);
ALTER TABLE exp.Material ADD
	CONSTRAINT FK_Material_ProtocolApplication FOREIGN KEY
	(
		SourceApplicationID
	) REFERENCES exp.ProtocolApplication (
		RowId
	);

CREATE INDEX IDX_CL_Material_RunId ON exp.Material(RunId);


ALTER TABLE exp.BioSource ADD
	CONSTRAINT FK_BioSource_Material FOREIGN KEY
	(
		MaterialId
	) REFERENCES exp.Material (
		RowId
	);

ALTER TABLE exp.MaterialInput ADD
	CONSTRAINT FK_MaterialInput_Material FOREIGN KEY
	(
		MaterialId
	) REFERENCES exp.Material (
		RowId
	);

ALTER TABLE exp.MaterialInput ADD
	CONSTRAINT FK_MaterialInput_ProtocolApplication FOREIGN KEY
	(
		TargetApplicationId
	) REFERENCES exp.ProtocolApplication (
		RowId
	);

CREATE INDEX IDX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId);

ALTER TABLE exp.ProtocolAction ADD
	CONSTRAINT FK_ProtocolAction_Parent_Protocol FOREIGN KEY
	(
		ParentProtocolId
	) REFERENCES exp.Protocol (
		RowId
	);

ALTER TABLE exp.ProtocolAction ADD
	CONSTRAINT FK_ProtocolAction_Child_Protocol FOREIGN KEY
	(
		ChildProtocolId
	) REFERENCES exp.Protocol (
		RowId
	);

ALTER TABLE exp.ProtocolActionPredecessor ADD
	CONSTRAINT FK_ActionPredecessor_Action_ProtocolAction FOREIGN KEY
	(
		ActionId
	) REFERENCES exp.ProtocolAction (
		RowId
	);

ALTER TABLE exp.ProtocolActionPredecessor ADD
	CONSTRAINT FK_ActionPredecessor_Predecessor_ProtocolAction FOREIGN KEY
	(
		PredecessorId
	) REFERENCES exp.ProtocolAction (
		RowId
	);

ALTER TABLE exp.ProtocolApplication ADD
	CONSTRAINT FK_ProtocolApplication_ExperimentRun FOREIGN KEY
	(
		RunId
	) REFERENCES exp.ExperimentRun (
		RowId
	);

ALTER TABLE exp.ProtocolApplication ADD
	CONSTRAINT FK_ProtocolApplication_Protocol FOREIGN KEY
	(
		ProtocolLSID
	) REFERENCES exp.Protocol (
		LSID
	);

CREATE INDEX IDX_CL_ProtocolApplication_RunId ON exp.ProtocolApplication(RunId);

CREATE INDEX IDX_ProtocolApplication_ProtocolLSID ON exp.ProtocolApplication(ProtocolLSID);


ALTER TABLE exp.ProtocolParameter ADD
	CONSTRAINT FK_ProtocolParameter_Protocol FOREIGN KEY
	(
		ProtocolId
	) REFERENCES exp.Protocol (
		RowId
	);

ALTER TABLE exp.ProtocolApplicationParameter ADD
	CONSTRAINT FK_ProtocolAppParam_ProtocolApp FOREIGN KEY
	(
		ProtocolApplicationId
	) REFERENCES exp.ProtocolApplication (
		RowId
	);


