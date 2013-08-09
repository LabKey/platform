/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

-- Change some Specimen fields to bigint
DROP INDEX study.specimenevent.IX_SpecimenEvent_SpecimenId;
DROP INDEX study.vial.IX_Vial_SpecimenId;

ALTER TABLE study.specimenevent DROP CONSTRAINT FK_SpecimensEvents_Specimens;
ALTER TABLE study.vial DROP CONSTRAINT FK_Vial_Specimen;

ALTER TABLE study.specimenevent DROP CONSTRAINT PK_SpecimensEvents;
ALTER TABLE study.specimen DROP CONSTRAINT PK_Specimen;
ALTER TABLE study.vial DROP CONSTRAINT PK_Specimens;

ALTER TABLE study.specimenevent ALTER COLUMN rowid bigint NOT NULL;
ALTER TABLE study.specimenevent ALTER COLUMN vialid bigint NOT NULL;
ALTER TABLE study.vial ALTER COLUMN rowid bigint NOT NULL;
ALTER TABLE study.vial ALTER COLUMN specimenid bigint NOT NULL;
ALTER TABLE study.specimen ALTER COLUMN rowid bigint NOT NULL;

ALTER TABLE study.vial ADD CONSTRAINT PK_Specimens PRIMARY KEY (rowid);
ALTER TABLE study.specimen ADD CONSTRAINT PK_Specimen PRIMARY KEY (rowid);
ALTER TABLE study.specimenevent ADD CONSTRAINT PK_SpecimensEvents PRIMARY KEY (rowid);

ALTER TABLE study.vial ADD CONSTRAINT FK_Vial_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId);
ALTER TABLE study.specimenevent ADD CONSTRAINT FK_SpecimensEvents_Specimens FOREIGN KEY (VialId) REFERENCES study.Vial(RowId);

CREATE INDEX IX_Vial_SpecimenId ON study.vial(SpecimenId);
CREATE INDEX IX_SpecimenEvent_SpecimenId ON study.SpecimenEvent(VialId);

