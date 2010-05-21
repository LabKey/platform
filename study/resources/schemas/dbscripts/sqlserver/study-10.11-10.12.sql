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

/* Handle the possibility that branch release10.1 study-10.10-10.11.sql script has already run. */

/* Create studydata participant index if it doesn't exist. */
IF INDEXPROPERTY(OBJECT_ID(LOWER('study.studydata')), 'IX_StudyData_Participant', 'IndexID') IS NULL
BEGIN
    CREATE INDEX IX_StudyData_Participant ON study.StudyData(container, participantid)
END
GO

/* Create study.vial.AvailabilityReason column if it doesn't exist. */
IF NOT EXISTS(SELECT * from syscolumns WHERE Name = LOWER('AvailabilityReason') AND ID = OBJECT_ID(LOWER('study.Vial')))
BEGIN
    ALTER TABLE study.Vial ADD AvailabilityReason NVARCHAR(256)
END
GO

/* Create study.SampleAvailabilityRule table if it doesn't exist */
IF OBJECTPROPERTY(OBJECT_ID(LOWER('study.SampleAvailabilityRule')), 'IsTable') != 1
BEGIN
    CREATE TABLE study.SampleAvailabilityRule
    (
      RowId INT IDENTITY(1,1),
      Container EntityId NOT NULL,
      SortOrder INTEGER NOT NULL,
      RuleType NVARCHAR(50),
      RuleData NVARCHAR(250),
      MarkType NVARCHAR(30),
      CONSTRAINT PL_SampleAvailabilityRule PRIMARY KEY (RowId)
    )
END
GO
