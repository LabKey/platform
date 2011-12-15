/*
 * Copyright (c) 2011 LabKey Corporation
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

CREATE TABLE exp.AssayQCFlag
(
  RowId SERIAL NOT NULL,
  RunId INT NOT NULL,
  FlagType VARCHAR(40) NOT NULL,
  Description TEXT NULL,
  Comment TEXT NULL,
  Enabled BOOLEAN NOT NULL,
  Created TIMESTAMP NULL,
  CreatedBy INT NULL,
  Modified TIMESTAMP NULL,
  ModifiedBy INT NULL
);

ALTER TABLE exp.AssayQCFlag ADD CONSTRAINT PK_AssayQCFlag PRIMARY KEY (RowId);

ALTER TABLE exp.AssayQCFlag ADD CONSTRAINT FK_AssayQCFlag_EunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId);

CREATE INDEX IX_AssayQCFlag_RunId ON exp.AssayQCFlag(RunId);
