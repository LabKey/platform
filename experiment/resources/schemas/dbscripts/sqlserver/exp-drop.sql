/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

-- DROP all views (current and obsolete)

-- NOTE: Don't remove any of these drop statements, even if we stop re-creating the view in *-create.sql. Drop statements must
-- remain in place so we can correctly upgrade from older versions, which we commit to for two years after each release.

EXEC core.fn_dropifexists 'MaterialSourceWithProject', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ObjectPropertiesView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExperimentRunMaterialOutputs', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ObjectClasses', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'AllLsidContainers', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExperimentRunDataInputs', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExperimentRunMaterialInputs', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExperimentRunDataOutputs', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'AllLsid', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'OutputDataForNode', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'OutputMaterialForNode', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'MarkedOutputDataForRun', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'MarkedOutputMaterialForRun', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ChildDataForApplication', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ChildMaterialForApplication', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorAllDataView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorRunStartDataView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorOutputDataView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorAllMaterialsView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorRunStartMaterialsView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorOutputMaterialsView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ProtocolActionPredecessorLSIDView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ProtocolActionStepDetailsView', 'exp', 'VIEW', NULL
GO
