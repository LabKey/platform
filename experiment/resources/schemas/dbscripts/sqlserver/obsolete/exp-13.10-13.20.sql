/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

/* exp-13.10-13.11.sql */

-- Use a container-scoped sequence for lists; each folder maintains a distinct, auto-incrementing sequence of List IDs

-- We're changing the exp.List PK from (RowId) to (Container, ListId), but we still need to keep the RowId around
-- since the index tables need it. (When we convert lists to hard tables we can drop the index tables and the RowId.)

-- First, drop FKs that depend on the RowId PK, add the unique constraint, and then recreate the FKs
ALTER TABLE exp.IndexInteger DROP CONSTRAINT FK_IndexInteger_List;
ALTER TABLE exp.IndexVarchar DROP CONSTRAINT FK_IndexVarchar_List;
ALTER TABLE exp.List DROP CONSTRAINT PK_List;
ALTER TABLE exp.List ADD CONSTRAINT UQ_RowId UNIQUE (RowId);
ALTER TABLE exp.IndexInteger ADD CONSTRAINT FK_IndexInteger_List FOREIGN KEY(ListId) REFERENCES exp.List(RowId);
ALTER TABLE exp.IndexVarchar ADD CONSTRAINT FK_IndexVarchar_List FOREIGN KEY(ListId) REFERENCES exp.List(RowId);

-- Now add ListId column...
ALTER TABLE exp.List ADD ListId INT NULL;
GO

-- ...populate it with the current values of RowId...
UPDATE exp.List SET ListId = RowId;
ALTER TABLE exp.List ALTER COLUMN ListId INT NOT NULL;
GO

-- ...and create the new PK (Container, ListId)
ALTER TABLE exp.List ADD CONSTRAINT PK_List PRIMARY KEY (Container, ListId);

-- Use Java code to create & populate sequences, initializing each current value to the maximum list ID in each folder
EXEC core.executeJavaUpgradeCode 'createListSequences';

/* exp-13.11-13.12.sql */

-- add start time, end time, and record count to protocol application table for ETL tasks and others
ALTER TABLE exp.ProtocolApplication ADD StartTime DATETIME NULL;
ALTER TABLE exp.ProtocolApplication ADD EndTime DATETIME NULL;
ALTER TABLE exp.ProtocolApplication ADD RecordCount INT NULL;
