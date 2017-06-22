/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

/* study-14.20-14.21.sql */

-- clean up some bad participantsequencenum values seen in the wild
DELETE FROM Study.ParticipantVisit WHERE ParticipantSequenceNum = 'NULL';

/* study-14.21-14.22.sql */

-- Change all the location booleans to NOT NULLABLE, #21616
UPDATE study.Site SET Repository = 0 WHERE Repository IS NULL;
ALTER TABLE study.Site ALTER COLUMN Repository BIT NOT NULL;
ALTER TABLE study.Site ADD DEFAULT 0 FOR Repository;

UPDATE study.Site SET Clinic = 0 WHERE Clinic IS NULL;
ALTER TABLE study.Site ALTER COLUMN Clinic BIT NOT NULL;
ALTER TABLE study.Site ADD DEFAULT 0 FOR Clinic;

UPDATE study.Site SET SAL = 0 WHERE SAL IS NULL;
ALTER TABLE study.Site ALTER COLUMN SAL BIT NOT NULL;
ALTER TABLE study.Site ADD DEFAULT 0 FOR SAL;

UPDATE study.Site SET EndPoint = 0 WHERE EndPoint IS NULL;
ALTER TABLE study.Site ALTER COLUMN EndPoint BIT NOT NULL;
ALTER TABLE study.Site ADD DEFAULT 0 FOR EndPoint;