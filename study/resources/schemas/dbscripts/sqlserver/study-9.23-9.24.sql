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


ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData;
ALTER TABLE study.StudyData DROP CONSTRAINT PK_ParticipantDataset;
GO

-- convert varchar columns to nvarchar to ensure correct joins with the other nvarchar columns in the system:
ALTER TABLE study.StudyData ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL;
ALTER TABLE study.StudyData ALTER COLUMN LSID NVARCHAR(200) NOT NULL;
ALTER TABLE study.StudyData ALTER COLUMN SourceLSID NVARCHAR(200);
GO

ALTER TABLE study.StudyData ADD
	CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key),
	CONSTRAINT PK_ParticipantDataset PRIMARY KEY (LSID);
GO

ALTER TABLE study.UploadLog DROP CONSTRAINT UQ_UploadLog_FilePath;
GO

-- Convert varchar columns to nvarchar to ensure correct joins with the other nvarchar columns in the system.
-- Note that we need to shorten this column from 512 to 450 characters to stay under
-- SQL Server's maximum key length of 900 bytes.
ALTER TABLE study.UploadLog	ALTER COLUMN FilePath NVARCHAR(400);
ALTER TABLE study.UploadLog	ALTER COLUMN Status NVARCHAR(20);
GO

ALTER TABLE study.UploadLog ADD
	CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath);
GO