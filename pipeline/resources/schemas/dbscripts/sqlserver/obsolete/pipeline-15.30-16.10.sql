/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

/* pipeline-15.30-15.31.sql */

ALTER TABLE pipeline.PipelineRoots DROP COLUMN KeyBytes;
ALTER TABLE pipeline.PipelineRoots DROP COLUMN CertBytes;
ALTER TABLE pipeline.PipelineRoots DROP COLUMN KeyPassword;

/* pipeline-15.31-15.32.sql */

DELETE FROM pipeline.PipelineRoots WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE pipeline.PipelineRoots ADD
    CONSTRAINT FK_PipelineRoots_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

ALTER TABLE pipeline.PipelineRoots ADD
    CONSTRAINT UQ_PipelineRoots_Container_Type UNIQUE (Container, Type);