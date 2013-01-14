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

-- Checked in after 12.3 initially shipped. Also checked in as query-12.32-12.33 since it's safe to rerun.
-- BE SURE TO CONSOLIDATE QUERY MODULE SCRIPTS STARTING WITH 12.301 for the 13.1 release.

ALTER TABLE query.customview ALTER COLUMN "schema" NVARCHAR(200);
