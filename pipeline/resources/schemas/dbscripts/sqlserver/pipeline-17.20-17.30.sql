/*
 * Copyright (c) 2017 LabKey Corporation
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
/* pipeline-17.20-17.21.sql */

CREATE TABLE pipeline.TriggerConfigurations
(
  RowId INT IDENTITY(1, 1) NOT NULL,
  Container ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,

  Name NVARCHAR(255) NOT NULL,
  Description NVARCHAR(MAX),
  Type NVARCHAR(255) NOT NULL,
  Enabled BIT,
  Configuration NVARCHAR(MAX),
  PipelineId NVARCHAR(255) NOT NULL,
  LastChecked DATETIME,

  CONSTRAINT PK_TriggerConfigurations PRIMARY KEY (RowId),
  CONSTRAINT FK_TriggerConfigurations_Container FOREIGN KEY (Container) REFERENCES core.Containers (ENTITYID),
  CONSTRAINT UQ_TriggerConfigurations_Name UNIQUE (Container, Name)
);