/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
-- DROP all views (current and obsolete).
-- NOTE: Never remove any of these drop statements, even if we stop using the view.  These drop statements must remain
--   in place so we can correctly upgrade from older versions.  If you're not convinced, talk to adam.
EXEC core.fn_dropifexists 'SpecimenSummary', 'study', 'VIEW', NULL
EXEC core.fn_dropifexists 'SpecimenDetail', 'study', 'VIEW', NULL
EXEC core.fn_dropifexists 'VialCounts', 'study', 'VIEW', NULL
EXEC core.fn_dropifexists 'LockedSpecimens', 'study', 'VIEW', NULL
EXEC core.fn_dropifexists 'ParticipantGroupCohortUnion', 'study', 'VIEW', NULL
GO
