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
-- drop this index because it is already a unique constraint
-- format of Drop proc
-- select core.fn_dropifexists(<tablename>, <schemaname>, 'INDEX', <indexname>
select core.fn_dropifexists('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Job')
;
-- drop this index because we can change the code to always pass container with jobparent
select core.fn_dropifexists('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_JobParent')
;
-- drop this index so we can add the jobparent column to it
select core.fn_dropifexists('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container')
;

-- in case this has alreaady been run, drop the new index before tryint to create it
select core.fn_dropifexists('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container_JobParent')
;

CREATE INDEX IX_StatusFiles_Container_JobParent ON pipeline.StatusFiles 
(Container, JobParent)
;
