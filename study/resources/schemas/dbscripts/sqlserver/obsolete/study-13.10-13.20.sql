/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

/* study-13.11-13.12.sql */

-- To change the PK, it is more efficient to drop all other indexes (including unique constraints),
-- drop and recreate PK, and then rebuild indexes

ALTER TABLE study.ParticipantVisit DROP CONSTRAINT UQ_ParticipantVisit_ParticipantSequenceNum;

-- changing order of keys to make supporting index useful for Container+Participant queries
ALTER TABLE study.ParticipantVisit DROP CONSTRAINT PK_ParticipantVisit;

-- Consider:  do we need a unique constraint on ParticipantSequenceNum if we have separate ones on Participant, SequenceNum ??
DROP INDEX study.ParticipantVisit.IX_ParticipantVisit_Container;
DROP INDEX study.ParticipantVisit.IX_ParticipantVisit_ParticipantId;
DROP INDEX study.ParticipantVisit.IX_ParticipantVisit_ParticipantSequenceNum;
DROP INDEX study.ParticipantVisit.IX_ParticipantVisit_SequenceNum;

-- Was previously Container, SequenceNum, ParticipantId
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY CLUSTERED
  (Container, ParticipantId, SequenceNum);


ALTER TABLE study.ParticipantVisit ADD CONSTRAINT UQ_ParticipantVisit_ParticipantSequenceNum UNIQUE
(ParticipantSequenceNum ASC, Container ASC);

CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit (ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit (SequenceNum);

/* study-13.12-13.13.sql */

ALTER TABLE study.specimenevent ADD Obsolete BIT NOT NULL DEFAULT 0;
