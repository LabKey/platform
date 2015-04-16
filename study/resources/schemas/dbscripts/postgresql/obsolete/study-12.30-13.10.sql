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

ALTER TABLE study.Study ADD AllowReqLocRepository BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocClinic BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocSAL BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocEndpoint BOOLEAN NOT NULL DEFAULT true;

/* study-12.31-12.32.sql */

ALTER TABLE study.specimenevent ALTER COLUMN externalid TYPE bigint;

/* study-12.32-12.33.sql */

ALTER TABLE study.study ADD ParticipantAliasDatasetName VARCHAR(200);
 ALTER TABLE study.study ADD ParticipantAliasSourceColumnName VARCHAR(200);
 ALTER TABLE study.study ADD ParticipantAliasColumnName VARCHAR(200);

/* study-12.33-12.34.sql */

ALTER TABLE study.study DROP ParticipantAliasDatasetName;

ALTER TABLE study.study ADD ParticipantAliasDatasetId INT;

ALTER TABLE study.study RENAME ParticipantAliasSourceColumnName TO ParticipantAliasSourceProperty;
ALTER TABLE study.study RENAME ParticipantAliasColumnName TO ParticipantAliasProperty;

/* study-12.34-12.35.sql */

-- Track participant indexing in the participant table now
ALTER TABLE study.Participant ADD LastIndexed TIMESTAMP NULL;

/* study-12.36-12.37.sql */

ALTER TABLE study.SpecimenDerivative ALTER COLUMN LdmsDerivativeCode TYPE VARCHAR(30);

/* study-12.37-12.38.sql */

ALTER TABLE study.SpecimenEvent ADD COLUMN InputHash BYTEA NULL;