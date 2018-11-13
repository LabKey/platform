/*
 * Copyright (c) 2018 LabKey Corporation
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
CREATE TABLE exp.ProtocolInput
(
  RowId SERIAL NOT NULL,
  Name VARCHAR(300) NOT NULL,
  LSID LSIDtype NOT NULL,
  ProtocolId INT NOT NULL,
  Input BOOLEAN NOT NULL,

  -- One of 'Material' or 'Data'
  ObjectType VARCHAR(8) NOT NULL,

  -- DataClassId may be non-null when ObjectType='Data'
  DataClassId INT NULL,
  -- MaterialSourceId may be non-null when ObjectType='Material'
  MaterialSourceId INT NULL,

  CriteriaName VARCHAR(50) NULL,
  CriteriaConfig TEXT NULL,
  MinOccurs INT NOT NULL,
  MaxOccurs INT NULL,

  CONSTRAINT PK_ProtocolInput_RowId PRIMARY KEY (RowId),
  CONSTRAINT FK_ProtocolInput_ProtocolId FOREIGN KEY (ProtocolId) REFERENCES exp.Protocol (RowId),
  CONSTRAINT FK_ProtocolInput_DataClassId FOREIGN KEY (DataClassId) REFERENCES exp.DataClass (RowId),
  CONSTRAINT FK_ProtocolInput_MaterialSourceId FOREIGN KEY (MaterialSourceId) REFERENCES exp.MaterialSource (RowId)
);

CREATE INDEX IX_ProtocolInput_ProtocolId ON exp.ProtocolInput (ProtocolId);
CREATE INDEX IX_ProtocolInput_DataClassId ON exp.ProtocolInput (DataClassId);
CREATE INDEX IX_ProtocolInput_MaterialSourceId ON exp.ProtocolInput (MaterialSourceId);


-- Add reference from MaterialInput to the ProtocolInputId that it corresponds to
ALTER TABLE exp.MaterialInput
    ADD COLUMN ProtocolInputId INT NULL;

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT FK_MaterialInput_ProtocolInput FOREIGN KEY (ProtocolInputId) REFERENCES exp.ProtocolInput (RowId);

CREATE INDEX IX_MaterialInput_ProtocolInputId ON exp.MaterialInput (ProtocolInputId);


-- Add reference from DataInput to the ProtocolInputId that it corresponds to
ALTER TABLE exp.DataInput
  ADD COLUMN ProtocolInputId INT NULL;

ALTER TABLE exp.DataInput
  ADD CONSTRAINT FK_DataInput_ProtocolInput FOREIGN KEY (ProtocolInputId) REFERENCES exp.ProtocolInput (RowId);

CREATE INDEX IX_DataInput_ProtocolInputId ON exp.DataInput (ProtocolInputId);

-- Issue 35817 - widen column to allow for longer paths and file names
ALTER TABLE exp.Data ALTER COLUMN DataFileURL TYPE VARCHAR(600);