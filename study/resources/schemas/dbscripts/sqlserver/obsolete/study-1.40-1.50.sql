/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
ALTER TABLE study.SampleRequestStatus ADD
    FinalState BIT NOT NULL DEFAULT 0,
    SpecimensLocked BIT NOT NULL DEFAULT 1
GO

ALTER TABLE study.Specimen
    ALTER COLUMN Ptid NVARCHAR(32)
GO

ALTER TABLE study.Participant ADD
    EnrollmentSiteId INT NULL,
    CurrentSiteId INT NULL,
	CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
	CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId)
GO

ALTER TABLE study.SpecimenEvent
    DROP CONSTRAINT UQ_Specimens_ScharpId
GO
