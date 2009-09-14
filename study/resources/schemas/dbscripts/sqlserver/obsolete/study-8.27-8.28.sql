/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
CREATE TABLE study.SpecimenComment
       (
       RowId INT IDENTITY(1,1),
       Container ENTITYID NOT NULL,
       SpecimenNumber NVARCHAR(50) NOT NULL,
       GlobalUniqueId NVARCHAR(50) NOT NULL,
       CreatedBy USERID,
       Created DATETIME,
       ModifiedBy USERID,
       Modified DATETIME,
       Comment NTEXT,
       CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
       );

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenNumber ON study.SpecimenComment(SpecimenNumber);
