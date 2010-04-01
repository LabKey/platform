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


/* pipeline-0.00-2.00.sql */

/* pipeline-0.00-1.00.sql */

EXEC sp_addapprole 'pipeline', 'password'
GO


CREATE TABLE pipeline.StatusFiles
    (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME DEFAULT GETDATE(),
	ModifiedBy USERID,
	Modified DATETIME DEFAULT GETDATE(),

	Container ENTITYID NOT NULL,
	EntityId ENTITYID NOT NULL,

	RowId INT IDENTITY(1,1) NOT NULL,
	Status NVARCHAR(100),
	Info NVARCHAR(255),
	FilePath NVARCHAR(500),
	Email NVARCHAR(255)

	CONSTRAINT PK_StatusFiles PRIMARY KEY (RowId)
    )
CREATE INDEX IX_StatusFiles_Status ON pipeline.StatusFiles (Status)
GO

CREATE TABLE pipeline.PipelineRoots
    (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME DEFAULT GETDATE(),
	ModifiedBy USERID,
	Modified DATETIME DEFAULT GETDATE(),

	Container ENTITYID NOT NULL,
	EntityId ENTITYID NOT NULL,

	PipelineRootId INT IDENTITY(1,1) NOT NULL,
	Path  NVARCHAR(300) NOT NULL,
	Providers VARCHAR(100),

	CONSTRAINT PK_PipelineRoots PRIMARY KEY (PipelineRootId)
    )
GO

/* pipeline-1.00-1.10.sql */

ALTER TABLE pipeline.StatusFiles ADD
    Description NVARCHAR(255),
    DataUrl NVARCHAR(255),
    Job uniqueidentifier,
    Provider NVARCHAR(255)
GO

UPDATE pipeline.StatusFiles SET Provider = 'X!Tandem (Cluster)'
WHERE FilePath LIKE '%/xtan/%'

UPDATE pipeline.StatusFiles SET Provider = 'Comet (Cluster)'
WHERE FilePath LIKE '%/cmt/%'

UPDATE pipeline.StatusFiles SET Provider = 'msInspect (Cluster)'
WHERE FilePath LIKE '%/inspect/%'
GO

/* pipeline-1.10-1.20.sql */

ALTER TABLE pipeline.PipelineRoots ADD
    Type NVARCHAR(255) NOT NULL DEFAULT 'PRIMARY'
GO

ALTER TABLE pipeline.StatusFiles ADD
    HadError bit
GO

/* pipeline-1.20-1.30.sql */

ALTER TABLE pipeline.StatusFiles DROP COLUMN
    HadError
GO

ALTER TABLE pipeline.StatusFiles ADD
    HadError bit NOT NULL DEFAULT 0
GO

UPDATE pipeline.StatusFiles SET HadError = 1
WHERE Status = 'ERROR'
GO

/* pipeline-1.50-1.60.sql */

DELETE FROM pipeline.StatusFiles
    WHERE Container NOT IN (SELECT EntityId FROM core.Containers)
GO

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
GO

/* pipeline-1.70-2.00.sql */

ALTER TABLE pipeline.StatusFiles
    ALTER COLUMN FilePath VARCHAR(1024)
ALTER TABLE pipeline.StatusFiles
    ALTER COLUMN Info VARCHAR(1024)
ALTER TABLE pipeline.StatusFiles
    ALTER COLUMN DataUrl VARCHAR(1024)
GO

/* pipeline-2.00-2.10.sql */

EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container'
GO

-- dropping this index because it seems very nonselective and has been the cause(?) of some deadlocks
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Status'
GO

CREATE INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container)
GO