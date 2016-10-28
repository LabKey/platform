/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

CREATE TABLE core.Notifications
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,

  UserId USERID NOT NULL,
  ObjectId NVARCHAR(64) NOT NULL,
  Type NVARCHAR(200) NOT NULL,

  CONSTRAINT PK_Notifications PRIMARY KEY (RowId),
  CONSTRAINT FK_Notifications_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
  CONSTRAINT UQ_Notifications_ContainerUserObjectType UNIQUE (Container, UserId, ObjectId, Type)
);