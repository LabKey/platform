/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
