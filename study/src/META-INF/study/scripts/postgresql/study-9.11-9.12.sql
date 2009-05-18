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
CREATE INDEX IX_ParticipantVisit_Container ON study.ParticipantVisit(Container);
CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit(ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit(SequenceNum);

ALTER TABLE study.SampleRequestSpecimen
  ADD Orphaned BOOLEAN NOT NULL DEFAULT False;

UPDATE study.SampleRequestSpecimen SET Orphaned = True WHERE RowId IN (
	SELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen
	LEFT OUTER JOIN study.Specimen ON
		study.Specimen.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND
		study.Specimen.Container = study.SampleRequestSpecimen.Container
	WHERE study.Specimen.GlobalUniqueId IS NULL
);