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
ALTER TABLE study.DataSet ADD COLUMN KeyColumn VARCHAR(50);             -- Property name in TypeURI

ALTER TABLE study.StudyData ADD COLUMN _key VARCHAR(200) NULL;          -- assay key column, used only on INSERT for UQ index

ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;

ALTER TABLE study.studydata
    ADD CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key);
