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

/* pipeline-8.30-8.31.sql */

UPDATE pipeline.StatusFiles SET JobParent = NULL
    WHERE JobParent NOT IN (SELECT Job FROM pipeline.StatusFiles WHERE Job IS NOT NULL);

UPDATE pipeline.StatusFiles SET Job = EntityId WHERE Job IS NULL;

/* pipeline-8.31-8.32.sql */

SELECT core.fn_dropifexists('StatusFiles', 'pipeline', 'CONSTRAINT', 'FK_StatusFiles_JobParent');

/* pipeline-8.32-8.33.sql */

SELECT core.fn_dropifexists('StatusFiles', 'pipeline', 'CONSTRAINT', 'UQ_StatusFiles_Job');

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_Job UNIQUE (Job);

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_JobParent FOREIGN KEY (JobParent) REFERENCES pipeline.StatusFiles(Job);