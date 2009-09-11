/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

CREATE SCHEMA pipeline;
SET search_path TO pipeline,public;

CREATE TABLE StatusFiles
    (
       _ts TIMESTAMP DEFAULT now(),
       CreatedBy USERID,
       Created TIMESTAMP DEFAULT now(),
       ModifiedBy USERID,
       Modified TIMESTAMP DEFAULT now(),

       Container ENTITYID NOT NULL,
       EntityId ENTITYID NOT NULL,

       RowId SERIAL,
       Status VARCHAR(100),
       Info VARCHAR(255),
       FilePath VARCHAR(500),
       Email VARCHAR(255),

       CONSTRAINT PK_StatusFiles PRIMARY KEY (RowId)
    );


CREATE INDEX IX_StatusFiles_Status ON StatusFiles (Status);

CREATE TABLE PipelineRoots
    (
       _ts TIMESTAMP DEFAULT now(),
       CreatedBy USERID,
       Created TIMESTAMP DEFAULT now(),
       ModifiedBy USERID,
       Modified TIMESTAMP DEFAULT now(),

       Container ENTITYID NOT NULL,
       EntityId ENTITYID NOT NULL,

       PipelineRootId SERIAL,
       Path VARCHAR(300) NOT NULL,
       Providers VARCHAR(100),

       CONSTRAINT PK_PipelineRoots PRIMARY KEY (PipelineRootId)
    );

