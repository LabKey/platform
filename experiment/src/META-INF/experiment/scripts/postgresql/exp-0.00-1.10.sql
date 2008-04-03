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
	Container ENTITYID NOT NULL ,
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
	Container ENTITYID NOT NULL
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
	Container ENTITYID NOT NULL
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
	Container ENTITYID NOT NULL
) ;

CREATE TABLE exp.MaterialInput (
	MaterialId int NOT NULL ,
	TargetApplicationId int NOT NULL
) ;

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
	Container ENTITYID NOT NULL
    );

ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT PK_MaterialSource PRIMARY KEY (RowId);
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_Name UNIQUE (Name);
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_LSID UNIQUE (LSID);

CREATE TABLE exp.BioSource (
	MaterialId int NOT NULL ,
	Individual VARCHAR(50) NULL ,
	SampleOriginDate TIMESTAMP NULL
) ;

CREATE TABLE exp.Object
	(
	ObjectId SERIAL NOT NULL,
	Container ENTITYID NOT NULL,
	ObjectURI LSIDType NOT NULL,
	OwnerObjectId int NULL,
	CONSTRAINT PK_Object PRIMARY KEY (ObjectId),
	CONSTRAINT UQ_Object UNIQUE (Container, ObjectURI)
	)
;

CREATE INDEX IDX_Object_OwnerObjectId ON exp.Object (OwnerObjectId);
-- CONSIDER: CONSTRAINT (Container, OwnerObjectId) --> (Container, ObjectId)

CREATE TABLE exp.ObjectProperty
	(
	ObjectId int NOT NULL,  -- FK exp.Object
	PropertyId int NOT NULL, -- FK exp.PropertyDescriptor
	TypeTag char(1) NOT NULL, -- s string, f float, d datetime, t text
	FloatValue float NULL,
	DateTimeValue timestamp NULL,
	StringValue varchar(400) NULL,
	TextValue text NULL,
	CONSTRAINT PK_ObjectProperty PRIMARY KEY (ObjectId, PropertyId)
	)
;


CREATE TABLE exp.PropertyDescriptor (
	RowId SERIAL NOT NULL,
	PropertyURI VARCHAR(200) NOT NULL,
	OntologyURI VARCHAR (200)  NULL,
	TypeURI VARCHAR(200) NULL,
	Name VARCHAR(50) NULL ,
	Description TEXT NULL,
	ValueType VARCHAR(50) NULL,
	DatatypeURI VARCHAR(200) NOT NULL DEFAULT 'http://www.w3.org/2001/XMLSchema#string',
    CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (RowId),
    CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI)
	);


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
	Container ENTITYID NOT NULL
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

ALTER TABLE exp.Object ADD
	CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId)
		REFERENCES exp.Object (ObjectId)
;

ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId)
		REFERENCES exp.Object (ObjectId)
;

ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId)
		REFERENCES exp.PropertyDescriptor (RowId)
;

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


CREATE VIEW exp.ChildMaterialForApplication AS
	SELECT     exp.Material.RowId, exp.Material.LSID, exp.Material.Name, exp.Material.SourceApplicationId, exp.Material.SourceProtocolLSID, exp.Material.RunId, exp.Material.Created,
	                      exp.ProtocolApplication.RowId AS ApplicationID, exp.ProtocolApplication.LSID AS ApplicationLSID, exp.ProtocolApplication.Name AS ApplicationName,
	                      exp.ProtocolApplication.CpasType AS ApplicationType
	FROM         exp.Material INNER JOIN
	                      exp.ProtocolApplication ON exp.Material.SourceApplicationId = exp.ProtocolApplication.RowId
	WHERE     (exp.ProtocolApplication.CpasType <> N'ExperimentRunOutput')
;


CREATE VIEW exp.ChildDataForApplication AS
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



CREATE VIEW exp.MarkedOutputDataForRun AS
	SELECT     exp.Data.RowId, exp.Data.LSID, exp.Data.Name, exp.Data.SourceApplicationId, exp.Data.SourceProtocolLSID, exp.Data.DataFileUrl, exp.Data.RunId, exp.Data.Created,
	                      PAStartNode.RowId AS ApplicationID, PAStartNode.LSID AS ApplicationLSID, PAStartNode.Name AS ApplicationName,
	                      PAStartNode.CpasType AS ApplicationCpasType
	FROM         exp.Data INNER JOIN
	                      exp.DataInput ON exp.Data.RowId = exp.DataInput.DataId INNER JOIN
	                      exp.ProtocolApplication PAMarkOutputNode ON exp.DataInput.TargetApplicationId = PAMarkOutputNode.RowId INNER JOIN
	                      exp.ProtocolApplication PAStartNode ON PAMarkOutputNode.RunId = PAStartNode.RunId
	WHERE     (PAMarkOutputNode.CpasType = N'ExperimentRunOutput') AND (PAStartNode.CpasType = N'ExperimentRun')
;


CREATE VIEW exp.OutputMaterialForNode AS
	SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
	FROM         exp.ChildMaterialforApplication
	UNION ALL
	SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
	FROM         exp.MarkedOutputMaterialForRun
;

CREATE VIEW exp.OutputDataForNode AS
	SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  DataFileUrl, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
	FROM         exp.ChildDataforApplication
	UNION ALL
	SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  DataFileUrl, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
	FROM         exp.MarkedOutputDataForRun
;


CREATE VIEW exp.AllLsid AS
	Select LSID, 'Protocol' AS Type From exp.Protocol UNION
	Select LSID, 'ProtocolApplication' AS TYPE From exp.ProtocolApplication UNION
	Select LSID, 'Experiment' AS TYPE From exp.Experiment UNION
	Select LSID, 'Material' AS TYPE From exp.Material UNION
	Select LSID, 'MaterialSource' AS TYPE From exp.MaterialSource UNION
	Select LSID, 'Data' AS TYPE From exp.Data UNION
	Select LSID, 'ExperimentRun' AS TYPE From exp.ExperimentRun
;


CREATE VIEW exp.ExperimentRunDataOutputs AS
	SELECT exp.Data.LSID AS DataLSID, exp.ExperimentRun.LSID AS RunLSID, exp.ExperimentRun.Container AS Container
	FROM exp.Data
	JOIN exp.ExperimentRun ON exp.Data.RunId=exp.ExperimentRun.RowId
	WHERE SourceApplicationId IS NOT NULL
;


CREATE VIEW exp.ExperimentRunMaterialInputs AS
	SELECT exp.ExperimentRun.LSID AS RunLSID, exp.Material.*
	FROM exp.ExperimentRun
	JOIN exp.ProtocolApplication PAExp ON exp.ExperimentRun.RowId=PAExp.RunId
	JOIN exp.MaterialInput ON exp.MaterialInput.TargetApplicationId=PAExp.RowId
	JOIN exp.Material ON exp.MaterialInput.MaterialId=exp.Material.RowId
	WHERE PAExp.CpasType='ExperimentRun'
;


CREATE VIEW exp.ExperimentRunDataInputs AS
	SELECT exp.ExperimentRun.LSID AS RunLSID, exp.Data.*
	FROM exp.ExperimentRun
	JOIN exp.ProtocolApplication PAExp ON exp.ExperimentRun.RowId=PAExp.RunId
	JOIN exp.DataInput ON exp.DataInput.TargetApplicationId=PAExp.RowId
	JOIN exp.Data ON exp.DataInput.DataId=exp.Data.RowId
	WHERE PAExp.CpasType='ExperimentRun'
;

CREATE OR REPLACE VIEW exp.AllLsidContainers AS
	SELECT LSID, Container, 'Protocol' AS Type FROM exp.Protocol UNION ALL
	SELECT exp.ProtocolApplication.LSID, Container, 'ProtocolApplication' AS Type FROM exp.ProtocolApplication JOIN exp.Protocol ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID UNION ALL
	SELECT LSID, Container, 'Experiment' AS Type FROM exp.Experiment UNION ALL
	SELECT LSID, Container, 'Material' AS Type FROM exp.Material UNION ALL
	SELECT LSID, Container, 'MaterialSource' AS Type FROM exp.MaterialSource UNION ALL
	SELECT LSID, Container, 'Data' AS Type FROM exp.Data UNION ALL
	SELECT LSID, Container, 'ExperimentRun' AS Type FROM exp.ExperimentRun
;


CREATE OR REPLACE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.*,
		PD.name, PD.PropertyURI, PD.DatatypeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyID = PD.RowId
;



CREATE OR REPLACE FUNCTION exp.ensureObject(ENTITYID, LSIDType, INTEGER) RETURNS INTEGER AS '
DECLARE
	_container ALIAS FOR $1;
	_lsid ALIAS FOR $2;
	_ownerObjectId ALIAS FOR $3;
	_objectid INTEGER;
BEGIN
--	START TRANSACTION;
		_objectid := (SELECT ObjectId FROM exp.Object where Container=_container AND ObjectURI=_lsid);
		IF (_objectid IS NULL) THEN
			INSERT INTO exp.Object (Container, ObjectURI, OwnerObjectId) VALUES (_container, _lsid, _ownerObjectId);
			_objectid := currval(\'exp.object_objectid_seq\');
		END IF;
--	COMMIT;
	RETURN _objectid;
END;
' LANGUAGE plpgsql;

-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA',null)
-- SELECT * FROM exp.ObjectPropertiesView


CREATE OR REPLACE FUNCTION exp.deleteObject(ENTITYID, LSIDType) RETURNS void AS '
DECLARE
	_container ALIAS FOR $1;
	_lsid ALIAS FOR $2;
	_objectid INTEGER;
BEGIN
		_objectid := (SELECT ObjectId FROM exp.Object where Container=_container AND ObjectURI=_lsid);
		IF (_objectid IS NULL) THEN
			RETURN;
		END IF;
--	START TRANSACTION;
		DELETE FROM exp.ObjectProperty WHERE ObjectId IN
			(SELECT ObjectId FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid);
		DELETE FROM exp.ObjectProperty WHERE ObjectId = _objectid;
		DELETE FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid;
		DELETE FROM exp.Object WHERE ObjectId = _objectid;
--	COMMIT;
	RETURN;
END;
' LANGUAGE plpgsql;


-- SELECT exp.deleteObject('00000000-0000-0000-0000-000000000000', 'lsidA')
-- SELECT * FROM exp.ObjectPropertiesView


--
-- This is the most general set property method
--


CREATE OR REPLACE FUNCTION exp.setProperty(INTEGER, LSIDType, LSIDType, CHAR(1), FLOAT, varchar(400), timestamp, TEXT) RETURNS void AS '
DECLARE
	_objectid ALIAS FOR $1;
	_propertyuri ALIAS FOR $2;
	_datatypeuri ALIAS FOR $3;
	_tag ALIAS FOR $4;
	_f ALIAS FOR $5;
	_s ALIAS FOR $6;
	_d ALIAS FOR $7;
	_t ALIAS FOR $8;
	_propertyid INTEGER;
BEGIN
--	START TRANSACTION;
		_propertyid := (SELECT RowId FROM exp.PropertyDescriptor WHERE PropertyURI=_propertyuri);
		if (1=1 OR _propertyid IS NULL) THEN
			INSERT INTO exp.PropertyDescriptor (PropertyURI, DatatypeURI) VALUES (_propertyuri, _datatypeuri);
			_propertyid := currval(\'exp.propertydescriptor_rowid_seq\');
		END IF;
		DELETE FROM exp.ObjectProperty WHERE ObjectId=_objectid AND PropertyId=_propertyid;
		INSERT INTO exp.ObjectProperty (ObjectId, PropertyID, TypeTag, FloatValue, StringValue, DateTimeValue, TextValue)
			VALUES (_objectid, _propertyid, _tag, _f, _s, _d, _t);
--	COMMIT;
	RETURN;
END;
' LANGUAGE plpgsql;


-- SELECT exp.setProperty(13, 'lsidPROP', 'lsidTYPE', 'f', 1.0, null, null, null)
-- SELECT * FROM exp.ObjectPropertiesView

-- internal methods


CREATE OR REPLACE FUNCTION exp._insertFloatProperty(INTEGER, INTEGER, FLOAT) RETURNS void AS '
DECLARE
	_objectid ALIAS FOR $1;
	_propid ALIAS FOR $2;
	_float ALIAS FOR $3;
BEGIN
	IF (_propid IS NULL OR _objectid IS NULL)  THEN
		RETURN;
	END IF;
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue)
	VALUES (_objectid, _propid, \'f\', _float);
	RETURN;
END;
' LANGUAGE plpgsql;

-- SELECT exp._insertFloatProperty(13, 5, 101.0)


CREATE OR REPLACE FUNCTION exp._insertDateTimeProperty(INTEGER, INTEGER, TIMESTAMP) RETURNS void AS '
DECLARE
	_objectid ALIAS FOR $1;
	_propid ALIAS FOR $2;
	_datetime ALIAS FOR $3;
BEGIN
	IF (_propid IS NULL OR _objectid IS NULL)  THEN
		RETURN;
	END IF;
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, DateTimeValue)
	VALUES (_objectid, _propid, \'d\', _datetime);
	RETURN;
END;
' LANGUAGE plpgsql;



CREATE OR REPLACE FUNCTION exp._insertStringProperty(INTEGER, INTEGER, VARCHAR(400)) RETURNS void AS '
DECLARE
	_objectid ALIAS FOR $1;
	_propid ALIAS FOR $2;
	_string ALIAS FOR $3;
BEGIN
	IF (_propid IS NULL OR _objectid IS NULL)  THEN
		RETURN;
	END IF;
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, StringValue)
	VALUES (_objectid, _propid, \'s\', _string);
	RETURN;
END;
' LANGUAGE plpgsql;



--
-- Set the same property on multiple objects (e.g. impoirt a column of datea)
--
-- fast method for importing ObjectProperties (need to wrap with datalayer code)
--


CREATE OR REPLACE FUNCTION exp.setFloatProperties(_propertyid INTEGER,
	_objectid1 INTEGER, _float1 FLOAT,
	_objectid2 INTEGER, _float2 FLOAT,
	_objectid3 INTEGER, _float3 FLOAT,
	_objectid4 INTEGER, _float4 FLOAT,
	_objectid5 INTEGER, _float5 FLOAT,
	_objectid6 INTEGER, _float6 FLOAT,
	_objectid7 INTEGER, _float7 FLOAT,
	_objectid8 INTEGER, _float8 FLOAT,
	_objectid9 INTEGER, _float9 FLOAT,
	_objectid10 INTEGER, _float10 FLOAT
	) RETURNS void AS '
BEGIN
--	BEGIN TRANSACTION
		DELETE FROM exp.ObjectProperty WHERE PropertyId=_propertyid AND ObjectId IN (_objectid1, _objectid2, _objectid3, _objectid4, _objectid5, _objectid6, _objectid7, _objectid8, _objectid9, _objectid10);
		PERFORM exp._insertFloatProperty(_objectid1, _propertyid, _float1);
		PERFORM exp._insertFloatProperty(_objectid2, _propertyid, _float2);
		PERFORM exp._insertFloatProperty(_objectid3, _propertyid, _float3);
		PERFORM exp._insertFloatProperty(_objectid4, _propertyid, _float4);
		PERFORM exp._insertFloatProperty(_objectid5, _propertyid, _float5);
		PERFORM exp._insertFloatProperty(_objectid6, _propertyid, _float6);
		PERFORM exp._insertFloatProperty(_objectid7, _propertyid, _float7);
		PERFORM exp._insertFloatProperty(_objectid8, _propertyid, _float8);
		PERFORM exp._insertFloatProperty(_objectid9, _propertyid, _float9);
		PERFORM exp._insertFloatProperty(_objectid10, _propertyid, _float10);
--	COMMIT
	RETURN;
END;
' LANGUAGE plpgsql;


-- SELECT exp.setFloatProperties(4, 13, 100.0, 14, 101.0, 15, 102.0, 16, 104.0, null, null, null, null, null, null, null, null, null, null, null, null)
-- SELECT * FROM exp.Object
-- SELECT * FROM exp.PropertyDescriptor
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA',null)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidB',null)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidC',null)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidD',null)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidE',null)


CREATE OR REPLACE FUNCTION exp.setStringProperties(_propertyid INTEGER,
	_objectid1 INTEGER, _string1 VARCHAR(400),
	_objectid2 INTEGER, _string2 VARCHAR(400),
	_objectid3 INTEGER, _string3 VARCHAR(400),
	_objectid4 INTEGER, _string4 VARCHAR(400),
	_objectid5 INTEGER, _string5 VARCHAR(400),
	_objectid6 INTEGER, _string6 VARCHAR(400),
	_objectid7 INTEGER, _string7 VARCHAR(400),
	_objectid8 INTEGER, _string8 VARCHAR(400),
	_objectid9 INTEGER, _string9 VARCHAR(400),
	_objectid10 INTEGER, _string10 VARCHAR(400)
	) RETURNS void AS '
BEGIN
--	BEGIN TRANSACTION
		DELETE FROM exp.ObjectProperty WHERE PropertyId=_propertyid AND ObjectId IN (_objectid1, _objectid2, _objectid3, _objectid4, _objectid5, _objectid6, _objectid7, _objectid8, _objectid9, _objectid10);
		PERFORM exp._insertStringProperty(_objectid1, _propertyid, _string1);
		PERFORM exp._insertStringProperty(_objectid2, _propertyid, _string2);
		PERFORM exp._insertStringProperty(_objectid3, _propertyid, _string3);
		PERFORM exp._insertStringProperty(_objectid4, _propertyid, _string4);
		PERFORM exp._insertStringProperty(_objectid5, _propertyid, _string5);
		PERFORM exp._insertStringProperty(_objectid6, _propertyid, _string6);
		PERFORM exp._insertStringProperty(_objectid7, _propertyid, _string7);
		PERFORM exp._insertStringProperty(_objectid8, _propertyid, _string8);
		PERFORM exp._insertStringProperty(_objectid9, _propertyid, _string9);
		PERFORM exp._insertStringProperty(_objectid10, _propertyid, _string10);
--	COMMIT
	RETURN;
END;
' LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp.setDateTimeProperties(_propertyid INTEGER,
	_objectid1 INTEGER, _datetime1 timestamp,
	_objectid2 INTEGER, _datetime2 timestamp,
	_objectid3 INTEGER, _datetime3 timestamp,
	_objectid4 INTEGER, _datetime4 timestamp,
	_objectid5 INTEGER, _datetime5 timestamp,
	_objectid6 INTEGER, _datetime6 timestamp,
	_objectid7 INTEGER, _datetime7 timestamp,
	_objectid8 INTEGER, _datetime8 timestamp,
	_objectid9 INTEGER, _datetime9 timestamp,
	_objectid10 INTEGER, _datetime10 timestamp
	) RETURNS void AS '
BEGIN
--	BEGIN TRANSACTION
		DELETE FROM exp.ObjectProperty WHERE PropertyId=_propertyid AND ObjectId IN (_objectid1, _objectid2, _objectid3, _objectid4, _objectid5, _objectid6, _objectid7, _objectid8, _objectid9, _objectid10);
		PERFORM exp._insertDateTimeProperty(_objectid1, _propertyid, _datetime1);
		PERFORM exp._insertDateTimeProperty(_objectid2, _propertyid, _datetime2);
		PERFORM exp._insertDateTimeProperty(_objectid3, _propertyid, _datetime3);
		PERFORM exp._insertDateTimeProperty(_objectid4, _propertyid, _datetime4);
		PERFORM exp._insertDateTimeProperty(_objectid5, _propertyid, _datetime5);
		PERFORM exp._insertDateTimeProperty(_objectid6, _propertyid, _datetime6);
		PERFORM exp._insertDateTimeProperty(_objectid7, _propertyid, _datetime7);
		PERFORM exp._insertDateTimeProperty(_objectid8, _propertyid, _datetime8);
		PERFORM exp._insertDateTimeProperty(_objectid9, _propertyid, _datetime9);
		PERFORM exp._insertDateTimeProperty(_objectid10, _propertyid, _datetime10);
--	COMMIT
	RETURN;
END;
' LANGUAGE plpgsql;


