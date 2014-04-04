/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

/* exp-11.30-11.31.sql */

CREATE TABLE exp.AssayQCFlag
(
  RowId INT IDENTITY (1, 1) NOT NULL,
  RunId INT NOT NULL,
  FlagType VARCHAR(40) NOT NULL,
  Description TEXT NULL,
  Comment TEXT NULL,
  Enabled BIT NOT NULL,
  Created DATETIME  NULL,
  CreatedBy INT NULL,
  Modified DATETIME  NULL,
  ModifiedBy INT NULL
);

ALTER TABLE exp.AssayQCFlag ADD CONSTRAINT PK_AssayQCFlag PRIMARY KEY (RowId);

ALTER TABLE exp.AssayQCFlag ADD CONSTRAINT FK_AssayQCFlag_EunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId);

CREATE INDEX IX_AssayQCFlag_RunId ON exp.AssayQCFlag(RunId);

/* exp-11.31-11.32.sql */

ALTER TABLE exp.AssayQCFlag ADD IntKey1 INT NULL;
ALTER TABLE exp.AssayQCFlag ADD IntKey2 INT NULL;

CREATE INDEX IX_AssayQCFlag_IntKeys ON exp.AssayQCFlag(IntKey1, IntKey2);

/* exp-11.32-11.33.sql */

ALTER TABLE exp.AssayQCFlag ADD Key1 NVARCHAR(50);
ALTER TABLE exp.AssayQCFlag ADD Key2 NVARCHAR(50);

CREATE INDEX IX_AssayQCFlag_Keys ON exp.AssayQCFlag(Key1, Key2);

/* exp-11.33-11.34.sql */

ALTER TABLE exp.PropertyDescriptor ADD CreatedBy USERID NULL;
ALTER TABLE exp.PropertyDescriptor ADD Created DATETIME NULL;
ALTER TABLE exp.PropertyDescriptor ADD ModifiedBy USERID NULL;
ALTER TABLE exp.PropertyDescriptor ADD Modified DATETIME NULL;

/* exp-11.34-11.35.sql */

-- Clean up orphaned experiment objects that were not properly deleted when their container was deleted
-- Then, add real FKs to ensure we don't orphan rows in the future

-- First clean up memberships in run groups for orphaned runs and experiments
DELETE FROM exp.RunList WHERE ExperimentId IN (SELECT RowId FROM exp.Experiment WHERE Container NOT IN (SELECT EntityId FROM core.Containers));
DELETE FROM exp.RunList WHERE ExperimentRunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

-- Disconnect datas and materials from runs that are going away
UPDATE exp.Data SET RunId = NULL WHERE RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.containers));
UPDATE exp.Material SET RunId = NULL WHERE RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.containers));

UPDATE exp.Data SET SourceApplicationId = NULL WHERE SourceApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.containers)));
UPDATE exp.Material SET SourceApplicationId = NULL WHERE SourceApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.containers)));

-- Clean up ophaned runs and their objects
DELETE FROM exp.DataInput WHERE TargetApplicationId IN
	(SELECT RowId FROM exp.ProtocolApplication WHERE
		ProtocolLSID IN (SELECT Lsid FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers))
		OR RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.Containers)));

DELETE FROM exp.MaterialInput WHERE TargetApplicationId IN
	(SELECT RowId FROM exp.ProtocolApplication WHERE
		ProtocolLSID IN (SELECT Lsid FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers))
		OR RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.Containers)));

DELETE FROM exp.ProtocolApplicationParameter WHERE ProtocolApplicationId IN
	(SELECT RowId FROM exp.ProtocolApplication WHERE
		ProtocolLSID IN (SELECT Lsid FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers))
		OR RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.Containers)));

DELETE FROM exp.ProtocolApplication WHERE
	ProtocolLSID IN (SELECT Lsid FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers))
	OR RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

DELETE FROM exp.AssayQCFlag WHERE RunId IN (SELECT RowId FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

DELETE FROM exp.ExperimentRun WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

-- Now that runs that might have been pointing to them for their batch are deleted, clean up orphaned experiments/run groups/batches
DELETE FROM exp.Experiment WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

-- Next, clean up orphaned protocols
DELETE FROM exp.ProtocolParameter WHERE ProtocolId IN (SELECT RowId FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

DELETE FROM exp.ProtocolActionPredecessor WHERE ActionId IN (SELECT RowId FROM exp.ProtocolAction WHERE
	ParentProtocolId IN (SELECT RowId FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers))
	OR ChildProtocolId IN (SELECT RowId FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers)));

DELETE FROM exp.ProtocolAction WHERE
	ParentProtocolId IN (SELECT RowId FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers))
	OR ChildProtocolId IN (SELECT RowId FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

DELETE FROM exp.Protocol WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

-- Delete orphaned sample sets/material sources
DELETE FROM exp.ActiveMaterialSource WHERE MaterialSourceLSID IN (SELECT Lsid FROM exp.MaterialSource WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

DELETE FROM exp.MaterialSource WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

-- Next, work on properties and domains
-- Start by deleting from juntion table between properties and domains
DELETE FROM exp.PropertyDomain WHERE
	PropertyId IN (SELECT PropertyId FROM exp.PropertyDescriptor WHERE Container NOT IN (SELECT EntityId FROM core.Containers))
	OR DomainId IN (SELECT DomainId FROM exp.DomainDescriptor WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

-- Then orphaned domains
DELETE FROM exp.DomainDescriptor WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

-- Next, orphaned validators and their usages
DELETE FROM exp.ValidatorReference WHERE
	ValidatorId IN (SELECT RowId FROM exp.PropertyValidator WHERE Container IN (SELECT EntityId FROM core.Containers))
	OR PropertyId IN (SELECT PropertyId FROM exp.PropertyDescriptor WHERE Container IN (SELECT EntityId FROM core.Containers));

DELETE FROM exp.PropertyValidator WHERE Container IN (SELECT EntityId FROM core.Containers);

-- Clean up conditional formats attached to delete property descriptors
DELETE FROM exp.ConditionalFormat WHERE PropertyId IN (SELECT PropertyId FROM exp.PropertyDescriptor WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

-- Then delete orphaned properties and their values
DELETE FROM exp.ObjectProperty WHERE PropertyId IN (SELECT PropertyId FROM exp.PropertyDescriptor WHERE Container NOT IN (SELECT EntityId FROM core.Containers));

DELETE FROM exp.PropertyDescriptor WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

-- Delete orphaned lists too
DELETE FROM exp.List WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

-- Finally, add some FKs so we don't get into this horrible state again
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT FK_DomainDescriptor_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.Experiment ADD CONSTRAINT FK_Experiment_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.ExperimentRun ADD CONSTRAINT FK_ExperimentRun_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.List ADD CONSTRAINT FK_List_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.MaterialSource ADD CONSTRAINT FK_MaterialSource_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT FK_PropertyDescriptor_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.PropertyValidator ADD CONSTRAINT FK_PropertyValidator_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.Protocol ADD CONSTRAINT FK_Protocol_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

CREATE INDEX idx_propertyvalidator_container ON exp.PropertyValidator (Container);

/* exp-11.35-11.36.sql */

-- Clean up DataInputs that were attached as run outputs in error
DELETE FROM exp.DataInput WHERE DataId IN
  (SELECT di.DataId
   FROM exp.DataInput di, exp.Data d, exp.ExperimentRun r
   WHERE di.DataId = d.RowId
     AND d.RunId = r.RowId
     AND r.lsid LIKE '%:NabAssayRun.%'
     AND r.protocollsid LIKE '%:NabAssayProtocol.%'
     AND di.role LIKE '%;%.xls')