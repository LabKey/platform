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

ALTER TABLE study.Specimen ADD
  DerivativeTypeId2 INT,
  FrozenTime DATETIME,
  ProcessingTime DATETIME,
  PrimaryVolume FLOAT,
  PrimaryVolumeUnits NVARCHAR(20),
  CONSTRAINT FK_Specimens_Derivatives2 FOREIGN KEY (DerivativeTypeId2) REFERENCES study.SpecimenDerivative(RowId)

GO

ALTER TABLE study.SpecimenEvent ADD
    ShippedFromLab INT,
    ShippedToLab INT,
    CONSTRAINT FK_ShippedFromLab_Site FOREIGN KEY (ShippedFromLab) references study.Site(RowId),
    CONSTRAINT FK_ShippedToLab_Site FOREIGN KEY (ShippedToLab) references study.Site(RowId)

GO

CREATE INDEX IX_SpecimenEvent_ShippedFromLab ON study.SpecimenEvent(ShippedFromLab)
CREATE INDEX IX_SpecimenEvent_ShippedToLab ON study.SpecimenEvent(ShippedToLab)

GO

ALTER TABLE study.Site ALTER COLUMN LabUploadCode NVARCHAR(10);
GO

ALTER TABLE study.SpecimenAdditive ALTER COLUMN LdmsAdditiveCode NVARCHAR(30);
GO

ALTER TABLE study.SpecimenDerivative ALTER COLUMN LdmsDerivativeCode NVARCHAR(20);
GO

ALTER TABLE study.Specimen ALTER COLUMN GlobalUniqueId NVARCHAR(50) NOT NULL;
ALTER TABLE study.Specimen ALTER COLUMN ClassId NVARCHAR(20);
ALTER TABLE study.Specimen ALTER COLUMN ProtocolNumber NVARCHAR(20);
ALTER TABLE study.Specimen ALTER COLUMN VisitDescription NVARCHAR(10);
ALTER TABLE study.Specimen ALTER COLUMN VolumeUnits NVARCHAR(20);
ALTER TABLE study.Specimen ALTER COLUMN SubAdditiveDerivative NVARCHAR(50);
GO

ALTER TABLE study.SpecimenEvent ALTER COLUMN UniqueSpecimenId NVARCHAR(50);
ALTER TABLE study.SpecimenEvent ALTER COLUMN RecordSource NVARCHAR(20);
ALTER TABLE study.SpecimenEvent ALTER COLUMN OtherSpecimenId NVARCHAR(50);
ALTER TABLE study.SpecimenEvent ALTER COLUMN XSampleOrigin NVARCHAR(50);
ALTER TABLE study.SpecimenEvent ALTER COLUMN SpecimenCondition NVARCHAR(30);
ALTER TABLE study.SpecimenEvent ALTER COLUMN ExternalLocation NVARCHAR(50);
GO