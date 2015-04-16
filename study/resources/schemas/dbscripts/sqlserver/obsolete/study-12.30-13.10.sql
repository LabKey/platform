/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

/* study-12.30-12.31.sql */

ALTER TABLE study.Study ADD AllowReqLocRepository BIT NOT NULL DEFAULT 1;
ALTER TABLE study.Study ADD AllowReqLocClinic BIT NOT NULL DEFAULT 1;
ALTER TABLE study.Study ADD AllowReqLocSAL BIT NOT NULL DEFAULT 1;
ALTER TABLE study.Study ADD AllowReqLocEndpoint BIT NOT NULL DEFAULT 1;

/* study-12.31-12.32.sql */

ALTER TABLE study.specimenevent ALTER COLUMN externalid bigint NOT NULL;

/* study-12.32-12.33.sql */

ALTER TABLE study.study ADD ParticipantAliasDatasetName NVARCHAR(200);
 ALTER TABLE study.study ADD ParticipantAliasSourceColumnName NVARCHAR(200);
 ALTER TABLE study.study ADD ParticipantAliasColumnName NVARCHAR(200);

/* study-12.33-12.34.sql */

ALTER TABLE study.study DROP COLUMN ParticipantAliasDatasetName;

ALTER TABLE study.study ADD ParticipantAliasDatasetId INT;

EXEC sp_rename 'study.study.ParticipantAliasSourceColumnName', 'ParticipantAliasSourceProperty', 'COLUMN';
EXEC sp_rename 'study.study.ParticipantAliasColumnName', 'ParticipantAliasProperty', 'COLUMN';

/* study-12.34-12.35.sql */

-- Track participant indexing in the participant table now
ALTER TABLE study.Participant ADD LastIndexed DATETIME NULL;

/* study-12.35-12.36.sql */

ALTER TABLE study.SpecimenDerivative ALTER COLUMN LdmsDerivativeCode NVARCHAR(30);

/* study-12.37-12.38.sql */

ALTER TABLE study.SpecimenEvent ADD InputHash BINARY(16) NULL;