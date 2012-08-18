/*
 * Copyright (c) 2012 LabKey Corporation
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

-- Clean up DataInputs that were attached as run outputs in error
DELETE FROM exp.DataInput WHERE DataId IN
  (SELECT di.DataId
   FROM exp.DataInput di, exp.Data d, exp.ExperimentRun r
   WHERE di.DataId = d.RowId
     AND d.RunId = r.RowId
     AND r.lsid LIKE '%:NabAssayRun.%'
     AND r.protocollsid LIKE '%:NabAssayProtocol.%'
     AND di.role LIKE '%;%.xls');

