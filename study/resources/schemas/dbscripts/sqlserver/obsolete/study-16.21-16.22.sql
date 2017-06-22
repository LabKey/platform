/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
CREATE TABLE study.DoseAndRoute
(
  RowId INT IDENTITY(1, 1) NOT NULL,
  Label NVARCHAR(600),
  Dose NVARCHAR(200),
  Route NVARCHAR(200),
  ProductId INT NOT NULL,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_DoseAndRoute PRIMARY KEY (RowId),
  CONSTRAINT DoseAndRoute_Dose_Route_ProductId UNIQUE (Dose, Route, ProductId)
);

EXEC core.executeJavaUpgradeCode 'populateDoseAndRoute';
