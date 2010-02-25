/*
 * Copyright (c) 2010 LabKey Corporation
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

/* study-9.30-9.301.sql */

ALTER TABLE study.study
  ALTER COLUMN datebased DROP DEFAULT,
  ALTER COLUMN datebased TYPE varchar(15)
    USING CASE WHEN datebased THEN 'RELATIVE_DATE' ELSE 'VISIT' END,
  ALTER COLUMN datebased SET NOT NULL;

ALTER TABLE study.study RENAME COLUMN datebased TO TimepointType;

/* study-9.301-9.302.sql */

ALTER TABLE study.study
  ADD COLUMN SubjectNounSingular VARCHAR(50) NOT NULL DEFAULT 'Participant',
  ADD COLUMN SubjectNounPlural VARCHAR(50) NOT NULL DEFAULT 'Participants',
  ADD COLUMN SubjectColumnName VARCHAR(50) NOT NULL DEFAULT 'ParticipantId';

/* study-9.302-9.303.sql */

ALTER TABLE study.Vial ADD FirstProcessedByInitials VARCHAR(32);
ALTER TABLE study.Specimen ADD FirstProcessedByInitials VARCHAR(32);

/* study-9.303-9.304.sql */

UPDATE study.Study SET timepointType='DATE' WHERE timepointType='RELATIVE_DATE';
UPDATE study.Study SET timepointType='CONTINUOUS' WHERE timepointType='ABSOLUTE_DATE';