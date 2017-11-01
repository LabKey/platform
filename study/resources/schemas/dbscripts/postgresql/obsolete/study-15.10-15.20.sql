/*
* Copyright (c) 2015-2017 LabKey Corporation
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

/* study-15.10-15.11.sql */

-- used by shared dataset definitions, specifies if data is shared across folders
--   NONE: (default) data is not shared across folders, same as any other container filtered table
--   ALL:  rows are all shared, visible in all study folders containing this dataset
--   PTID: rows are all shared, and are visible if PTID is a found in study.participants for the current folder
ALTER TABLE study.dataset ADD COLUMN dataSharing VARCHAR(20) NOT NULL DEFAULT 'NONE';

/* study-15.11-15.12.sql */

UPDATE study.participantvisit SET visitrowid=-1 WHERE visitrowid IS NULL;
ALTER TABLE study.participantvisit ALTER COLUMN visitrowid SET DEFAULT -1;
ALTER TABLE study.participantvisit ALTER COLUMN visitrowid SET NOT NULL;