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

/* pipeline-9.20-9.21.sql */

DECLARE @default sysname
SELECT @default = object_name(cdefault)
FROM syscolumns
WHERE id = object_id('pipeline.PipelineRoots')
AND name = 'PerlPipeline'
EXEC ('ALTER TABLE pipeline.PipelineRoots DROP CONSTRAINT ' + @default)
GO

ALTER TABLE pipeline.PipelineRoots DROP COLUMN PerlPipeline
GO

/* pipeline-9.21-9.22.sql */

-- drop this index because it is already a unique constraint
-- format of Drop proc
-- exec core.fn_dropifexists <tablename>, <schemaname>, 'INDEX', <indexname>
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Job'
go
-- drop this index because we can change the code to always pass container with jobparent
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_JobParent'
go
-- drop this index so we can add the jobparent column to it
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container'
go
-- in case this has alreaady been run, drop the new index before tryint to create it
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container_JobParent'
go

CREATE CLUSTERED INDEX IX_StatusFiles_Container_JobParent ON pipeline.StatusFiles 
(Container ASC, JobParent ASC)
go