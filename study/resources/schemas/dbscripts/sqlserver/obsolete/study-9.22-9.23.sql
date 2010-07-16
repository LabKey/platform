/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

ALTER TABLE study.ParticipantVisit ADD
	ParticipantSequenceKey NVARCHAR(200)
GO

UPDATE study.ParticipantVisit SET ParticipantSequenceKey = ParticipantID + '|' + CAST(SequenceNum AS NVARCHAR)
GO

ALTER TABLE study.ParticipantVisit ADD
    CONSTRAINT UQ_StudyData_ParticipantSequenceKey UNIQUE (ParticipantSequenceKey, Container)
GO

CREATE INDEX IX_ParticipantVisit_ParticipantSequenceKey ON study.ParticipantVisit(ParticipantSequenceKey, Container)
GO

ALTER TABLE study.StudyData ADD ParticipantSequenceKey NVARCHAR(200)
GO

UPDATE study.StudyData SET ParticipantSequenceKey = ParticipantID + '|' + CAST(SequenceNum AS NVARCHAR)
GO

CREATE INDEX IX_StudyData_ParticipantSequenceKey ON study.StudyData(ParticipantSequenceKey, Container)
GO

ALTER TABLE study.Specimen ADD ParticipantSequenceKey NVARCHAR(200)
GO

UPDATE study.Specimen SET ParticipantSequenceKey = PTID + '|' + CAST(VisitValue AS NVARCHAR)
GO

CREATE INDEX IX_Specimen_ParticipantSequenceKey ON study.Specimen(ParticipantSequenceKey, Container)
GO

CREATE INDEX IX_SpecimenPrimaryType_PrimaryType ON study.SpecimenPrimaryType(PrimaryType)
GO

CREATE INDEX IX_SpecimenDerivative_Derivative ON study.SpecimenDerivative(Derivative)
GO

CREATE INDEX IX_SpecimenAdditive_Additive ON study.SpecimenAdditive(Additive)
GO
