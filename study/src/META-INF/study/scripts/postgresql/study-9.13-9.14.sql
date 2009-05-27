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
ALTER TABLE study.Specimen
  ADD AtRepository BOOLEAN NOT NULL DEFAULT False,
  ADD LockedInRequest BOOLEAN NOT NULL DEFAULT False,
  ADD Available BOOLEAN NOT NULL DEFAULT False;

UPDATE study.Specimen SET AtRepository = TRUE
  WHERE CurrentLocation IN (SELECT ss.RowId FROM study.Site ss WHERE ss.Repository = TRUE);

UPDATE study.Specimen SET LockedInRequest = TRUE WHERE RowId IN
(
  SELECT study.Specimen.RowId FROM ((study.SampleRequest AS request
      JOIN study.SampleRequestStatus AS status ON request.StatusId = status.RowId AND status.SpecimensLocked = True)
      JOIN study.SampleRequestSpecimen AS map ON request.rowid = map.SampleRequestId AND map.Orphaned = False)
      JOIN study.Specimen ON study.Specimen.GlobalUniqueId = map.SpecimenGlobalUniqueId AND study.Specimen.Container = map.Container
);

UPDATE study.Specimen SET Available = 
(
  CASE Requestable
  WHEN True THEN (
      CASE LockedInRequest
      WHEN True THEN False
      ELSE True
      END)
  WHEN False THEN False
  ELSE (
  CASE AtRepository
      WHEN True THEN (
          CASE LockedInRequest
          WHEN True THEN False
          ELSE True
          END)
      ELSE False
      END)
  END
);
