/*
 * Copyright (c) 2010 LabKey Corporation
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

/* exp-9.30-9.31.sql */

ALTER TABLE exp.Data ADD COLUMN CreatedBy INT;
ALTER TABLE exp.Data ADD COLUMN ModifiedBy INT;
ALTER TABLE exp.Data ADD COLUMN Modified TIMESTAMP;

UPDATE exp.Data SET Modified = Created;

UPDATE exp.Data SET CreatedBy =
  (SELECT CreatedBy FROM exp.ExperimentRun WHERE exp.ExperimentRun.RowId = exp.Data.RunId);


ALTER TABLE exp.Material ADD COLUMN CreatedBy INT;
ALTER TABLE exp.Material ADD COLUMN ModifiedBy INT;
ALTER TABLE exp.Material ADD COLUMN Modified TIMESTAMP;

UPDATE exp.Material SET Modified = Created;

UPDATE exp.Material SET CreatedBy =
  (SELECT CreatedBy FROM exp.ExperimentRun WHERE exp.ExperimentRun.RowId = exp.Material.RunId);

UPDATE exp.Material SET CreatedBy =
  (SELECT CreatedBy FROM exp.MaterialSource WHERE exp.MaterialSource.LSID = exp.Material.CpasType)
  WHERE CreatedBy IS NULL;