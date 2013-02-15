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

CREATE TABLE geomicroarray.FeatureAnnotationSet (
  RowId SERIAL,
  "Name" VARCHAR(200) NOT NULL,
  Vendor VARCHAR(50),
  Container entityid NOT NULL,

  CONSTRAINT PK_FeatureAnnotationSet PRIMARY KEY (RowId),
  CONSTRAINT FK_FeatureAnnotationSet_Container FOREIGN KEY (Container) REFERENCES core.containers (entityid)
);

CREATE TABLE geomicroarray.FeatureAnnotation (
  RowId SERIAL,
  FeatureAnnotationSetId INT NOT NULL,

  ProbeId VARCHAR(128) NOT NULL,
  GeneSymbol TEXT,
  UniGeneId TEXT,
  GeneId TEXT,
  AccessionId VARCHAR(128),
  RefSeqProteinId TEXT,
  RefSeqTranscriptId TEXT,

  CONSTRAINT PK_FeatureAnnotation PRIMARY KEY (RowId),
  CONSTRAINT UQ_FeatureAnnotation_ProbeId_FeatureAnnotationSetId UNIQUE (ProbeId, FeatureAnnotationSetId),
  CONSTRAINT FK_FeatureAnnotation_FeatureAnnotationSetId FOREIGN KEY (FeatureAnnotationSetId) REFERENCES geomicroarray.FeatureAnnotationSet(RowId)
);
