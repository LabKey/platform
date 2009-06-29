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

ALTER TABLE study.Vial DROP
  COLUMN FrozenTime,
  COLUMN ProcessingTime,
  COLUMN ProcessedByInitials,
  COLUMN ProcessingDate
GO

ALTER TABLE study.Vial ADD
  PrimaryVolume FLOAT,
  PrimaryVolumeUnits NVARCHAR(20)
GO

UPDATE study.Vial SET
  PrimaryVolume = study.Specimen.PrimaryVolume,
  PrimaryVolumeUnits = study.Specimen.PrimaryVolumeUnits
FROM study.Specimen
WHERE study.Specimen.RowId = study.Vial.SpecimenId
GO

ALTER TABLE study.Specimen DROP
  COLUMN PrimaryVolume,
  COLUMN PrimaryVolumeUnits
GO