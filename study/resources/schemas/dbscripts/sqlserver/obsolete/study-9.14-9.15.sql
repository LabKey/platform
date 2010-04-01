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
ALTER TABLE study.Specimen ADD
  ProcessedByInitials NVARCHAR(32),
  ProcessingDate DATETIME,
  ProcessingLocation INT
GO

DROP INDEX study.SpecimenEvent.IX_SpecimenEvent_ShippedFromLab
GO
DROP INDEX study.SpecimenEvent.IX_SpecimenEvent_ShippedToLab
GO

ALTER TABLE study.SpecimenEvent ADD
  ProcessedByInitials NVARCHAR(32),
  ProcessingDate DATETIME
GO

ALTER TABLE study.SpecimenEvent DROP CONSTRAINT FK_ShippedFromLab_Site
GO
ALTER TABLE study.SpecimenEvent DROP CONSTRAINT FK_ShippedToLab_Site
GO

ALTER TABLE study.SpecimenEvent ALTER COLUMN ShippedFromLab NVARCHAR(32)
GO
ALTER TABLE study.SpecimenEvent ALTER COLUMN ShippedToLab NVARCHAR(32)
GO