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

SET search_path TO study, public;

CREATE TABLE LDMSSample
(
    Container ENTITYID NOT NULL,
    RowId INT NOT NULL, -- PK, FK exp.Material
    Clasid VARCHAR(4), -- Group identifier; VTN, HPTN
    Lstudy FLOAT,	-- Protocol number; 35, 039.1
    Specid VARCHAR(12), -- LDMS generated specimen number; Unique per primary specimen. The default configuration in LDMS is to assign a unique specimen number for each additive and each derivative. Unique across labs.
    Guspec VARCHAR(11) NOT NULL, -- LDMS generated global unique specimen ID; Unique per aliquot. Unique within a lab and across labs.
    Txtpid INT, -- Participant Identifier
    Drawd TIMESTAMP, -- Date specimen was drawn
    Vidval FLOAT, -- Visit value; 1, 2.1
    Vidstr VARCHAR(3),	-- Visit description; Day, Mo, Scr, Wk, but typically Vst
    Recdt TIMESTAMP, -- Date that specimen was received at site-affiliated lab
    Primstr VARCHAR(3), -- Primary specimen type; BLD, CER, GLU, DWB
    Addstr VARCHAR(3), -- Additive tube type; HEP, NON, EDT
    Dervstr VARCHAR(3), -- Derivative type; DBS, PLA, SER
    Sec_tp VARCHAR(3), -- Sub additive/derivative
    Volume FLOAT, -- Aliquot volume value
    Volstr VARCHAR(3), -- Volume units; CEL, MG, ML, UG, N/A
    Condstr VARCHAR(3), -- Condition string; Usually SAT
    Sec_id VARCHAR(15), -- Other specimen ID; Stored as Other Spec ID on specimen management form in LDMS
    Addtim FLOAT, -- Expected time value for PK or metabolic samples; 1.0
    Addunt INT, 	-- Expected time unit for PK or metabolic samples; Hr
    CONSTRAINT PK_LDMSSample PRIMARY KEY (RowId),
    CONSTRAINT UQ_LDMSSample UNIQUE (Container, Guspec),
    CONSTRAINT FK_LDMSSample_Material FOREIGN KEY (RowId) REFERENCES exp.Material(RowId)
);

CREATE TABLE LDMSStorageType
(
    StorageTypeId INT NOT NULL,
    Label VARCHAR(100),
    CONSTRAINT PK_StorageType PRIMARY KEY (StorageTypeId)
);

INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (0, 'Not stored in LDMS');
INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (2, 'Stored in LDMS');
INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (-3, 'Deleted from LDMS');

CREATE TABLE LDMSShipFlag
(
    ShipFlagId INT NOT NULL,
    Label VARCHAR(100),
    CONSTRAINT PK_ShipFlag PRIMARY KEY (ShipFlagId)
);

INSERT INTO study.LDMSShipFlag (ShipFlagId, Label) VALUES (0, 'Not shipped via LDMS');
INSERT INTO study.LDMSShipFlag (ShipFlagId, Label) VALUES (1, 'Shipped via LDMS');

CREATE TABLE LDMSSampleEvent
(
    LDMSSampleId INT NOT NULL, -- FK study.LDMSSample
    Container ENTITYID NOT NULL,
    Labid INT, -- LDMS lab number; 300 is JHU Central Lab
    Uspeci INT,	-- Unique specimen number; Used to link to base tables, unique at aliquot level. Unique within a lab, but can repeat across labs
    Parusp INT,	-- Parent unique specimen number; Unique per primary specimen within a lab, but can repeat across labs.
    Stored INT, -- Storage flag; 0=not stored in LDMS, 2=stored in LDMS, -3=permanently deleted from LDMS storage
    Stord TIMESTAMP, -- Date that specimen was stored in LDMS at each lab.
    Shipfg INT, -- Shipping flag; 0=not shipped via LDMS, 1=shipped via LDMS
    Shipno INT, -- LDMS generated shipping batch number; >0 for shipped batches
    Shipd TIMESTAMP, -- Date that specimen was shipped
    Rb_no INT, -- Imported batch number; >0 for imported batches. Does not match shipping batch number from other lab.
    Rlprot INT, -- Group/protocol Field
    Recvd TIMESTAMP, -- Date that specimen was received at subsequent lab. Should be equivalent to storage date at that lab.
    Commts VARCHAR(30), -- First 30 characters from comment field in specimen management
    CONSTRAINT FK_LDMSSampleEvent_LDMSSample FOREIGN KEY (LDMSSampleId) REFERENCES study.LDMSSample(RowId),
    CONSTRAINT FK_LDMSSampleEvent_Site FOREIGN KEY (Container,Labid) REFERENCES study.Site(Container,SiteId),
    CONSTRAINT FK_LDMSSampleEvent_SampleStorageType FOREIGN KEY (Stored) REFERENCES study.LDMSStorageType(StorageTypeId),
    CONSTRAINT FK_LDMSSampleEvent_SampleShipFlag FOREIGN KEY (Shipfg) REFERENCES study.LDMSShipFlag(ShipFlagId)
);