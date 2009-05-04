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

ALTER TABLE study.SpecimenComment ADD
  QualityControlFlag BIT NOT NULL DEFAULT 0,
  QualityControlFlagForced BIT NOT NULL DEFAULT 0,
  QualityControlComments NVARCHAR(512)
GO

ALTER TABLE study.SpecimenEvent ADD
  Ptid NVARCHAR(32),
  DrawTimestamp DATETIME,
  SalReceiptDate DATETIME,
  ClassId NVARCHAR(20),
  VisitValue NUMERIC(15,4),
  ProtocolNumber NVARCHAR(20),
  VisitDescription NVARCHAR(10),
  Volume double precision,
  VolumeUnits NVARCHAR(20),
  SubAdditiveDerivative NVARCHAR(50),
  PrimaryTypeId INT,
  DerivativeTypeId INT,
  AdditiveTypeId INT,
  DerivativeTypeId2 INT,
  OriginatingLocationId INT,
  FrozenTime DATETIME,
  ProcessingTime DATETIME,
  PrimaryVolume FLOAT,
  PrimaryVolumeUnits NVARCHAR(20)
GO