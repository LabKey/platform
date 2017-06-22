/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
/* comm-14.30-14.31.sql */

/* comm-14.30-14.31.sql */
CREATE TABLE comm.Tours
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Title NVARCHAR(500) NOT NULL,
  Description NVARCHAR(4000),
  Container ENTITYID NOT NULL,
  EntityId ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Json NVARCHAR(MAX),
  Mode INT NOT NULL DEFAULT 0,

  CONSTRAINT PK_ToursId PRIMARY KEY (RowId)
);