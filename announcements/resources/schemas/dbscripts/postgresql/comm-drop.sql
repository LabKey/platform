/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

SELECT core.fn_dropifexists('Threads', 'comm', 'VIEW', NULL);

-- This view was removed in 14.3, but we must leave drop statement in place until at least 17.1. See #20848
SELECT core.fn_dropifexists('PagesAndVersions', 'comm', 'VIEW', NULL);

SELECT core.fn_dropifexists('CurrentWikiVersions', 'comm', 'VIEW', NULL);
SELECT core.fn_dropifexists('AllWikiVersions', 'comm', 'VIEW', NULL);
SELECT core.fn_dropifexists('PagePaths', 'comm', 'VIEW', NULL);
