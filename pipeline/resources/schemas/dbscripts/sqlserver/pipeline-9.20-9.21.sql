/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

DECLARE @default sysname
SELECT @default = object_name(cdefault)
FROM syscolumns
WHERE id = object_id('pipeline.PipelineRoots')
AND name = 'PerlPipeline'
EXEC ('ALTER TABLE pipeline.PipelineRoots DROP CONSTRAINT ' + @default)
GO

ALTER TABLE pipeline.PipelineRoots DROP COLUMN PerlPipeline
GO

