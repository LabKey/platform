/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

/* pipeline-0.00-2.00.sql */

/* pipeline-0.00-1.00.sql */

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

/* pipeline-1.00-1.10.sql */

ALTER TABLE pipeline.StatusFiles
    ADD Description VARCHAR(255),
    ADD DataUrl VARCHAR(255),
    ADD Job VARCHAR(36),
    ADD Provider VARCHAR(255);

UPDATE pipeline.StatusFiles SET Provider = 'X!Tandem (Cluster)'
WHERE FilePath LIKE '%/xtan/%';

UPDATE pipeline.StatusFiles SET Provider = 'Comet (Cluster)'
WHERE FilePath LIKE '%/cmt/%';

UPDATE pipeline.StatusFiles SET Provider = 'msInspect (Cluster)'
WHERE FilePath LIKE '%/inspect/%';

/* pipeline-1.10-1.20.sql */

ALTER TABLE pipeline.PipelineRoots
    ADD Type VARCHAR(255) NOT NULL DEFAULT 'PRIMARY';

ALTER TABLE pipeline.StatusFiles
    ADD HadError boolean;

/* pipeline-1.20-1.30.sql */

ALTER TABLE pipeline.StatusFiles
    DROP COLUMN HadError;

ALTER TABLE pipeline.StatusFiles
    ADD HadError boolean NOT NULL DEFAULT FALSE;

UPDATE pipeline.StatusFiles SET HadError = TRUE
WHERE Status = 'ERROR';

/* pipeline-1.50-1.60.sql */

DELETE FROM pipeline.StatusFiles
    WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

/* pipeline-1.70-2.00.sql */

ALTER TABLE pipeline.StatusFiles
    ALTER FilePath TYPE VARCHAR(1024);
ALTER TABLE pipeline.StatusFiles
    ALTER Info TYPE VARCHAR(1024);
ALTER TABLE pipeline.StatusFiles
    ALTER DataUrl TYPE VARCHAR(1024);

/* pipeline-2.00-2.10.sql */

SELECT core.fn_dropifexists ('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container')
;

-- dropping this index because it seems very nonselective and has been the cause(?) of some deadlocks
SELECT core.fn_dropifexists ('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Status')
;

CREATE INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container)
;