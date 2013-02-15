/*
 * Copyright (c) 2013 LabKey Corporation
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

CREATE SCHEMA geomicroarray;
GO

CREATE TABLE geomicroarray.FeatureAnnotationSet (
  RowId INT IDENTITY (1,1) NOT NULL,
  "Name" NVARCHAR(200) NOT NULL,
  Vendor NVARCHAR(50),
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_FeatureAnnotationSet PRIMARY KEY (RowId),
  CONSTRAINT FK_FeatureAnnotationSet_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE TABLE geomicroarray.FeatureAnnotation (
  RowId INT IDENTITY (1,1) NOT NULL,
  FeatureAnnotationSetId INT NOT NULL,

  ProbeId NVARCHAR(128) NOT NULL,
  GeneSymbol NTEXT,
  UniGeneId NTEXT,
  GeneId NTEXT,
  AccessionId NVARCHAR(128),
  RefSeqProteinId NTEXT,
  RefSeqTranscriptId NTEXT,

  CONSTRAINT PK_FeatureAnnotation PRIMARY KEY (RowId),
  CONSTRAINT UQ_FeatureAnnotation_ProbeId_FeatureAnnotationSetId UNIQUE (ProbeId, FeatureAnnotationSetId),
  CONSTRAINT FK_FeatureAnnotation_FeatureAnnotationSetId FOREIGN KEY (FeatureAnnotationSetId) REFERENCES geomicroarray.FeatureAnnotationSet(RowId)
);
