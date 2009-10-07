/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

-- DROP current view.
-- NOTE: Don't remove this drop statement, even if we stop using the view.  This drop statement must remain
--   in place so we can correctly upgrade from older versions.  If you're not convinced, talk to adam.
SELECT core.fn_dropifexists('PropertyEntries', 'prop', 'VIEW', NULL);
