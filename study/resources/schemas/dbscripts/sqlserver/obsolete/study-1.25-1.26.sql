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
CREATE TABLE study.StudyData
(
	Container ENTITYID NOT NULL,
	ParticipantId VARCHAR(32) NOT NULL,
	VisitId INT NULL,
	DatasetId INT NOT NULL,
	LSID VARCHAR(200) NOT NULL,
	CONSTRAINT PK_ParticipantDataset PRIMARY KEY (LSID),
	CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
)
go
