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

ALTER TABLE study.Specimen ADD
  AtRepository BIT NOT NULL DEFAULT 0,
  LockedInRequest BIT NOT NULL DEFAULT 0,
  Available BIT NOT NULL DEFAULT 0
GO

UPDATE study.Specimen SET AtRepository = 1
  WHERE CurrentLocation IN (SELECT ss.RowId FROM study.Site ss WHERE ss.Repository = 1)
GO

UPDATE study.Specimen SET LockedInRequest = 1 WHERE RowId IN
(
  SELECT study.Specimen.RowId FROM ((study.SampleRequest AS request
      JOIN study.SampleRequestStatus AS status ON request.StatusId = status.RowId AND status.SpecimensLocked = 1)
      JOIN study.SampleRequestSpecimen AS map ON request.rowid = map.SampleRequestId AND map.Orphaned = 0)
      JOIN study.Specimen ON study.Specimen.GlobalUniqueId = map.SpecimenGlobalUniqueId AND study.Specimen.Container = map.Container
)
GO

UPDATE study.Specimen SET Available =
(
  CASE Requestable
  WHEN 1 THEN (
      CASE LockedInRequest
      WHEN 1 THEN 0
      ELSE 1
      END)
  WHEN 0 THEN 0
  ELSE (
  CASE AtRepository
      WHEN 1 THEN (
          CASE LockedInRequest
          WHEN 1 THEN 0
          ELSE 1
          END)
      ELSE 0
      END)
  END
)
GO
