/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
	Created TIMESTAMP NOT NULL 
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
	CpasType VARCHAR (50)  NULL ,
	SourceApplicationId int NULL ,
	SourceProtocolLSID LSIDtype NULL ,
	RunId int NULL ,
	Created TIMESTAMP NOT NULL 
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

CREATE INDEX IX_CL_Property_ParentURI ON exp.Property(ParentURI);


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

CREATE INDEX IX_CL_ExperimentRun_ExperimentLSID ON exp.ExperimentRun(ExperimentLSID);


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

CREATE INDEX IX_CL_Data_RunId ON exp.Data(RunId);


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

CREATE INDEX IX_DataInput_TargetApplicationId ON exp.DataInput(TargetApplicationId);


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

CREATE INDEX IX_CL_Material_RunId ON exp.Material(RunId);


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

CREATE INDEX IX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId);

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

CREATE INDEX IX_CL_ProtocolApplication_RunId ON exp.ProtocolApplication(RunId);

CREATE INDEX IX_ProtocolApplication_ProtocolLSID ON exp.ProtocolApplication(ProtocolLSID);


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

CREATE VIEW exp.ProtocolActionStepDetailsView AS
SELECT     Protocol_P.LSID AS ParentProtocolLSID, Protocol_C.LSID AS ChildProtocolLSID, exp.ProtocolAction.Sequence AS ActionSequence, 
                      exp.ProtocolAction.RowId AS ActionId, Protocol_C.RowId AS ProtocolId, Protocol_C.Name, Protocol_C.ProtocolDescription, Protocol_C.ApplicationType, 
                      Protocol_C.MaxInputMaterialPerInstance, Protocol_C.MaxInputDataPerInstance,
                      Protocol_C.OutputMaterialPerInstance, Protocol_C.OutputDataPerInstance, Protocol_C.OutputMaterialType, Protocol_C.OutputDataType,
                      Protocol_C.Instrument, Protocol_C.Software, Protocol_C.contactId,
                      Protocol_C.Created, Protocol_C.EntityId, Protocol_C.CreatedBy, Protocol_C.Modified, Protocol_C.ModifiedBy, Protocol_C.Container
FROM         exp.Protocol Protocol_C INNER JOIN
                      exp.ProtocolAction ON Protocol_C.RowId = exp.ProtocolAction.ChildProtocolId INNER JOIN
                      exp.Protocol Protocol_P ON exp.ProtocolAction.ParentProtocolId = Protocol_P.RowId;

CREATE VIEW exp.ProtocolActionPredecessorLSIDView AS
SELECT     Action.ParentProtocolLSID, Action.ChildProtocolLSID, Action.ActionSequence, PredecessorAction.ParentProtocolLSID AS PredecessorParentLSID, 
                      PredecessorAction.ChildProtocolLSID AS PredecessorChildLSID, PredecessorAction.ActionSequence AS PredecessorSequence
FROM         exp.ProtocolActionPredecessor INNER JOIN
                      exp.ProtocolActionStepDetailsView PredecessorAction ON exp.ProtocolActionPredecessor.PredecessorId = PredecessorAction.ActionId INNER JOIN
                      exp.ProtocolActionStepDetailsView Action ON exp.ProtocolActionPredecessor.ActionId = Action.ActionId                      ;

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
WHERE     (exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID <> exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID);

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
WHERE     (exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID);

CREATE VIEW exp.PredecessorAllMaterialsView AS
SELECT     *
FROM         exp.PredecessorRunStartMaterialsView
UNION
SELECT     *
FROM         exp.PredecessorOutputMaterialsView;

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
WHERE     (exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID <> exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID);

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
WHERE     (exp.ProtocolActionPredecessorLSIDView.PredecessorChildLSID = exp.ProtocolActionPredecessorLSIDView.PredecessorParentLSID);

CREATE VIEW exp.PredecessorAllDataView AS
SELECT     *
FROM         exp.PredecessorRunStartDataView
UNION
SELECT     *
FROM         exp.PredecessorOutputDataView;

CREATE VIEW exp.ChildMaterialForApplication
AS
SELECT     exp.Material.RowId, exp.Material.LSID, exp.Material.Name, exp.Material.SourceApplicationId, exp.Material.SourceProtocolLSID, exp.Material.RunId, exp.Material.Created, 
                      exp.ProtocolApplication.RowId AS ApplicationID, exp.ProtocolApplication.LSID AS ApplicationLSID, exp.ProtocolApplication.Name AS ApplicationName, 
                      exp.ProtocolApplication.CpasType AS ApplicationType
FROM         exp.Material INNER JOIN
                      exp.ProtocolApplication ON exp.Material.SourceApplicationId = exp.ProtocolApplication.RowId
WHERE     (exp.ProtocolApplication.CpasType <> N'ExperimentRunOutput')
;

CREATE VIEW exp.ChildDataForApplication
AS
SELECT     exp.Data.RowId, exp.Data.LSID, exp.Data.Name, exp.Data.SourceApplicationId, exp.Data.SourceProtocolLSID, exp.Data.DataFileUrl, exp.Data.RunId, exp.Data.Created, 
                      exp.ProtocolApplication.RowId AS ApplicationID, exp.ProtocolApplication.LSID AS ApplicationLSID, exp.ProtocolApplication.Name AS ApplicationName, 
                      exp.ProtocolApplication.CpasType AS ApplicationType
FROM         exp.Data INNER JOIN
                      exp.ProtocolApplication ON exp.Data.SourceApplicationId = exp.ProtocolApplication.RowId
WHERE     (exp.ProtocolApplication.CpasType <> N'ExperimentRunOutput')
;

CREATE VIEW exp.MarkedOutputMaterialForRun
AS

SELECT     exp.Material.RowId, exp.Material.LSID, exp.Material.Name, exp.Material.SourceApplicationId, exp.Material.SourceProtocolLSID, exp.Material.RunId, exp.Material.Created, 
                      PAStartNode.RowId AS ApplicationID, PAStartNode.LSID AS ApplicationLSID, PAStartNode.Name AS ApplicationName, 
                      PAStartNode.CpasType AS ApplicationCpasType
FROM         exp.Material INNER JOIN
                      exp.MaterialInput ON exp.Material.RowId = exp.MaterialInput.MaterialId INNER JOIN
                      exp.ProtocolApplication PAMarkOutputNode ON exp.MaterialInput.TargetApplicationId = PAMarkOutputNode.RowId INNER JOIN
                      exp.ProtocolApplication PAStartNode ON PAMarkOutputNode.RunId = PAStartNode.RunId
WHERE     (PAMarkOutputNode.CpasType = N'ExperimentRunOutput') AND (PAStartNode.CpasType = N'ExperimentRun')
;

CREATE VIEW exp.MarkedOutputDataForRun
AS

SELECT     exp.Data.RowId, exp.Data.LSID, exp.Data.Name, exp.Data.SourceApplicationId, exp.Data.SourceProtocolLSID, exp.Data.DataFileUrl, exp.Data.RunId, exp.Data.Created, 
                      PAStartNode.RowId AS ApplicationID, PAStartNode.LSID AS ApplicationLSID, PAStartNode.Name AS ApplicationName, 
                      PAStartNode.CpasType AS ApplicationCpasType
FROM         exp.Data INNER JOIN
                      exp.DataInput ON exp.Data.RowId = exp.DataInput.DataId INNER JOIN
                      exp.ProtocolApplication PAMarkOutputNode ON exp.DataInput.TargetApplicationId = PAMarkOutputNode.RowId INNER JOIN
                      exp.ProtocolApplication PAStartNode ON PAMarkOutputNode.RunId = PAStartNode.RunId
WHERE     (PAMarkOutputNode.CpasType = N'ExperimentRunOutput') AND (PAStartNode.CpasType = N'ExperimentRun')
;

CREATE VIEW exp.OutputMaterialForNode
AS

SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
FROM         exp.ChildMaterialforApplication
UNION ALL
SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
FROM         exp.MarkedOutputMaterialForRun
;

CREATE VIEW exp.OutputDataForNode
AS

SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  DataFileUrl, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
FROM         exp.ChildDataforApplication
UNION ALL
SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  DataFileUrl, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
FROM         exp.MarkedOutputDataForRun
;


