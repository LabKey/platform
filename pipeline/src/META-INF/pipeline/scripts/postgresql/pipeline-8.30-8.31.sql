/*
 * Copyright (c) 2008 LabKey Corporation
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

UPDATE pipeline.StatusFiles SET JobParent = NULL WHERE JobParent NOT IN (SELECT EntityId FROM pipeline.StatusFiles);

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_EntityId UNIQUE (EntityId);

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_JobParent FOREIGN KEY (JobParent) REFERENCES pipeline.StatusFiles(EntityId);
