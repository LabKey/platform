/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

DROP VIEW IF EXISTS exp.ObjectPropertiesView;
DROP VIEW IF EXISTS exp.ExperimentRunMaterialOutputs;
DROP VIEW IF EXISTS exp.ObjectClasses;
DROP VIEW IF EXISTS exp.AllLsidContainers;
DROP VIEW IF EXISTS exp.ExperimentRunDataInputs;
DROP VIEW IF EXISTS exp.ExperimentRunMaterialInputs;
DROP VIEW IF EXISTS exp.ExperimentRunDataOutputs;
DROP VIEW IF EXISTS exp.AllLsid;
DROP VIEW IF EXISTS exp.OutputDataForNode;
DROP VIEW IF EXISTS exp.OutputMaterialForNode;
DROP VIEW IF EXISTS exp.MarkedOutputDataForRun;
DROP VIEW IF EXISTS exp.MarkedOutputMaterialForRun;
DROP VIEW IF EXISTS exp.ChildDataForApplication;
DROP VIEW IF EXISTS exp.ChildMaterialForApplication;
DROP VIEW IF EXISTS exp.PredecessorAllDataView;
DROP VIEW IF EXISTS exp.PredecessorRunStartDataView;
DROP VIEW IF EXISTS exp.PredecessorOutputDataView;
DROP VIEW IF EXISTS exp.PredecessorAllMaterialsView;
DROP VIEW IF EXISTS exp.PredecessorRunStartMaterialsView;
DROP VIEW IF EXISTS exp.PredecessorOutputMaterialsView;
DROP VIEW IF EXISTS exp.ProtocolActionPredecessorLSIDView;
DROP VIEW IF EXISTS exp.ProtocolActionStepDetailsView;
