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

ALTER TABLE study.SpecimenComment
  ADD QualityControlFlag BOOLEAN NOT NULL DEFAULT False,
  ADD QualityControlFlagForced BOOLEAN NOT NULL DEFAULT False,
  ADD QualityControlComments VARCHAR(512);

ALTER TABLE study.SpecimenEvent
  ADD Ptid VARCHAR(32),
  ADD DrawTimestamp TIMESTAMP,
  ADD SalReceiptDate TIMESTAMP,
  ADD ClassId VARCHAR(20),
  ADD VisitValue NUMERIC(15,4),
  ADD ProtocolNumber VARCHAR(20),
  ADD VisitDescription VARCHAR(10),
  ADD Volume double precision,
  ADD VolumeUnits VARCHAR(20),
  ADD SubAdditiveDerivative VARCHAR(50),
  ADD PrimaryTypeId INT,
  ADD DerivativeTypeId INT,
  ADD AdditiveTypeId INT,
  ADD DerivativeTypeId2 INT,
  ADD OriginatingLocationId INT,
  ADD FrozenTime TIMESTAMP,
  ADD ProcessingTime TIMESTAMP,
  ADD PrimaryVolume FLOAT,
  ADD PrimaryVolumeUnits VARCHAR(20);

UPDATE study.SpecimenEvent SET
	Ptid = study.Specimen.Ptid,
	DrawTimestamp = study.Specimen.DrawTimestamp,
	SalReceiptDate = study.Specimen.SalReceiptDate,
	ClassId = study.Specimen.ClassId,
	VisitValue = study.Specimen.VisitValue,
	ProtocolNumber = study.Specimen.ProtocolNumber,
	VisitDescription = study.Specimen.VisitDescription,
	Volume = study.Specimen.Volume,
	VolumeUnits = study.Specimen.VolumeUnits,
	SubAdditiveDerivative = study.Specimen.SubAdditiveDerivative,
	PrimaryTypeId = study.Specimen.PrimaryTypeId,
	DerivativeTypeId = study.Specimen.DerivativeTypeId,
	AdditiveTypeId = study.Specimen.AdditiveTypeId,
	DerivativeTypeId2 = study.Specimen.DerivativeTypeId2,
	OriginatingLocationId = study.Specimen.OriginatingLocationId,
	FrozenTime = study.Specimen.FrozenTime,
	ProcessingTime = study.Specimen.ProcessingTime,
	PrimaryVolume = study.Specimen.PrimaryVolume,
	PrimaryVolumeUnits = study.Specimen.PrimaryVolumeUnits
FROM study.Specimen WHERE study.Specimen.RowId = study.SpecimenEvent.SpecimenId;