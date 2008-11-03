/*
 * Copyright (c) 2008 LabKey Corporation
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
  ADD SpecimenHash NVARCHAR(256);
GO

DROP INDEX study.SpecimenComment.IX_SpecimenComment_SpecimenNumber;
GO

ALTER TABLE study.SpecimenComment
    ADD SpecimenHash NVARCHAR(256);

ALTER TABLE study.SpecimenComment
    DROP COLUMN SpecimenNumber;
GO

CREATE INDEX IX_SpecimenComment_SpecimenHash ON study.SpecimenComment(Container, SpecimenHash);
CREATE INDEX IX_Specimen_SpecimenHash ON study.Specimen(Container, SpecimenHash);
GO