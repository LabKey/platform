/*
 * Copyright (c) 2009 LabKey Corporation
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

ALTER TABLE exp.Data ADD CreatedBy INT
GO
ALTER TABLE exp.Data ADD ModifiedBy INT
GO
ALTER TABLE exp.Data ADD Modified DATETIME
GO

UPDATE exp.Data SET Modified = Created
GO

UPDATE exp.Data SET CreatedBy =
  (SELECT CreatedBy FROM exp.ExperimentRun WHERE exp.ExperimentRun.RowId = exp.Data.RunId)
GO


ALTER TABLE exp.Material ADD CreatedBy INT
GO
ALTER TABLE exp.Material ADD ModifiedBy INT
GO
ALTER TABLE exp.Material ADD Modified DATETIME
GO

UPDATE exp.Material SET Modified = Created
GO

UPDATE exp.Material SET CreatedBy =
  (SELECT CreatedBy FROM exp.MaterialSource WHERE exp.MaterialSource.LSID = exp.Material.CpasType)
GO

UPDATE exp.Material SET CreatedBy =
  (SELECT CreatedBy FROM exp.ExperimentRun WHERE exp.ExperimentRun.RowId = exp.Material.RunId)
  WHERE CreatedBy IS NULL
GO

