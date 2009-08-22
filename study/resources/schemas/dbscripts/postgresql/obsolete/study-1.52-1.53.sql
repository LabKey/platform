/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
CREATE TABLE study.UploadLog
(
  RowId SERIAL,
  Container ENTITYID NOT NULL,
  Created TIMESTAMP NOT NULL,
  CreatedBy USERID NOT NULL,
  Description TEXT,
  FilePath VARCHAR(512),
  DatasetId INT NOT NULL,
  Status VARCHAR(20),
  CONSTRAINT PK_UploadLog PRIMARY KEY (RowId),
  CONSTRAINT FK_UploadLog_Dataset FOREIGN KEY (Container,DatasetId) REFERENCES study.Dataset (Container,DatasetId),
  CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath)
)