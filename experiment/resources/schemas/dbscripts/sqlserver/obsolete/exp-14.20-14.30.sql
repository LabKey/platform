/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

/* exp-14.20-14.21.sql */

--ALTER TABLE exp.PropertyDescriptor DROP StorageColumnName;

ALTER TABLE exp.PropertyDescriptor ADD StorageColumnName NVARCHAR(100) NULL;
GO

UPDATE exp.propertydescriptor
SET storagecolumnname=name
WHERE EXISTS (SELECT * FROM
  exp.propertydomain DP inner join exp.domaindescriptor DD on DP.domainid = DD.domainid
WHERE DD.storagetablename is not null and propertydescriptor.propertyid = DP.propertyid);

/* exp-14.21-14.22.sql */

-- Add batchId column to run table
ALTER TABLE exp.ExperimentRun
   ADD BatchId INT;

ALTER TABLE exp.ExperimentRun
  ADD CONSTRAINT FK_ExperimentRun_BatchId FOREIGN KEY (BatchId) REFERENCES exp.Experiment (RowId);

CREATE INDEX IX_ExperimentRun_BatchId
  ON exp.ExperimentRun(BatchId);

GO

UPDATE exp.ExperimentRun SET BatchId = (
  SELECT e.RowId AS BatchId
    FROM exp.Experiment e
    WHERE
      e.BatchProtocolId IS NOT NULL
      AND e.RowId = (
        SELECT MIN(ExperimentId) FROM exp.Experiment e, exp.RunList rl
        WHERE e.RowId = rl.ExperimentId
        AND rl.ExperimentRunId = ExperimentRun.RowId
      )
);