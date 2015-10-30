/*
 * Copyright (c) 2015 LabKey Corporation
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

/* core-15.20-15.21.sql */

CREATE TABLE core.Notifications
(
  RowId SERIAL,
  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,

  UserId USERID NOT NULL,
  ObjectId VARCHAR(64) NOT NULL,
  Type VARCHAR(200) NOT NULL,

  CONSTRAINT PK_Notifications PRIMARY KEY (RowId),
  CONSTRAINT FK_Notifications_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
  CONSTRAINT UQ_Notifications_ContainerUserObjectType UNIQUE (Container, UserId, ObjectId, Type)
);

/* core-15.21-15.22.sql */

ALTER TABLE core.Notifications ADD Description TEXT;
ALTER TABLE core.Notifications ADD ReadOn TIMESTAMP;
ALTER TABLE core.Notifications ADD ActionLinkText VARCHAR(2000);
ALTER TABLE core.Notifications ADD ActionLinkURL VARCHAR(4000);
ALTER TABLE core.Notifications DROP COLUMN ModifiedBy;
ALTER TABLE core.Notifications DROP COLUMN Modified;