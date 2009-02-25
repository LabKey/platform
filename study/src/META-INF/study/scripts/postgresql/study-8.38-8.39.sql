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

/*
LDMS Name 	Export Name 			Association
dervst2 	derivative_type_id_2 	the vial
froztm 	    frozen_time 			the vial
proctm 	    processing_time  		the vial
frlab       shipped_from_lab 	 	single vial location
tolab 	    shipped_to_lab 	 		single vial location
privol      primary_volume 	 		the draw
pvlunt 	    primary_volume_units 	the draw
*/

ALTER TABLE study.Specimen
  ADD DerivativeTypeId2 INT,
  ADD FrozenTime TIMESTAMP,
  ADD ProcessingTime TIMESTAMP,
  ADD PrimaryVolume FLOAT,
  ADD PrimaryVolumeUnits VARCHAR(20),
  ADD CONSTRAINT FK_Specimens_Derivatives2 FOREIGN KEY (DerivativeTypeId2) REFERENCES study.SpecimenDerivative(RowId);

ALTER TABLE study.SpecimenEvent
    ADD ShippedFromLab INT,
    ADD ShippedToLab INT,
    ADD CONSTRAINT FK_ShippedFromLab_Site FOREIGN KEY (ShippedFromLab) references study.Site(RowId),
    ADD CONSTRAINT FK_ShippedToLab_Site FOREIGN KEY (ShippedToLab) references study.Site(RowId);

CREATE INDEX IX_SpecimenEvent_ShippedFromLab ON study.SpecimenEvent(ShippedFromLab);
CREATE INDEX IX_SpecimenEvent_ShippedToLab ON study.SpecimenEvent(ShippedToLab);

ALTER TABLE study.Site
    ALTER COLUMN LabUploadCode TYPE VARCHAR(10);

ALTER TABLE study.SpecimenAdditive
    ALTER COLUMN LdmsAdditiveCode TYPE VARCHAR(30);

ALTER TABLE study.SpecimenDerivative
    ALTER COLUMN LdmsDerivativeCode TYPE VARCHAR(20);

ALTER TABLE study.Specimen
    ALTER COLUMN GlobalUniqueId TYPE VARCHAR(50),
    ALTER COLUMN ClassId TYPE VARCHAR(20),
    ALTER COLUMN ProtocolNumber TYPE VARCHAR(20),
    ALTER COLUMN VisitDescription TYPE VARCHAR(10),
    ALTER COLUMN VolumeUnits TYPE VARCHAR(20),
    ALTER COLUMN SubAdditiveDerivative TYPE VARCHAR(50);

ALTER TABLE study.SpecimenEvent
    ALTER COLUMN UniqueSpecimenId TYPE VARCHAR(50),
    ALTER COLUMN RecordSource TYPE VARCHAR(20),
    ALTER COLUMN OtherSpecimenId TYPE VARCHAR(50),
    ALTER COLUMN XSampleOrigin TYPE VARCHAR(50),
    ALTER COLUMN SpecimenCondition TYPE VARCHAR(30),
    ALTER COLUMN ExternalLocation TYPE VARCHAR(50);