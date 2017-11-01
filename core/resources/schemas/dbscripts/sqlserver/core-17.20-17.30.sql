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
/* core-17.20-17.21.sql */

CREATE TABLE core.QCState
(
  RowId INT IDENTITY(1,1),
  Label NVARCHAR(64) NULL,
  Description NVARCHAR(500) NULL,
  Container ENTITYID NOT NULL,
  PublicData BIT NOT NULL,

  CONSTRAINT PK_QCState PRIMARY KEY (RowId),
  CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
);