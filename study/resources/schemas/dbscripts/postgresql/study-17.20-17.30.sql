/*
 * Copyright (c) 2017 LabKey Corporation
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

/* study-17.20-17.21.sql */

ALTER TABLE study.AssaySpecimen ADD COLUMN DataSet INTEGER;

/* study-17.21-17.22.sql */

SELECT core.executeJavaUpgradeCode('moveQCStateToCore');

/* study-17.22-17.23.sql */

--Drop existing indexes, if they exist
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_sequencenum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_visitrowid');

--For Resync perf
CREATE INDEX ix_participantvisit_sequencenum ON study.participantvisit (container, participantid, sequencenum, ParticipantSequenceNum);
CREATE INDEX ix_participantvisit_visitrowid ON study.participantvisit (visitrowid);