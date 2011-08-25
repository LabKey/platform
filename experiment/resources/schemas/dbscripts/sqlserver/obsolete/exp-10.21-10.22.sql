/*
 * Copyright (c) 2010 LabKey Corporation
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

CREATE TABLE exp.ConditionalFormat (
    RowId INT IDENTITY(1,1) NOT NULL,
    SortOrder INT NOT NULL,
    PropertyId INT NOT NULL,
    Filter NVARCHAR(500) NOT NULL,
    Bold BIT NOT NULL,
    Italic BIT NOT NULL,
    Strikethrough BIT NOT NULL,
    TextColor NVARCHAR(10),
    BackgroundColor NVARCHAR(10),

    CONSTRAINT PK_ConditionalFormat_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_ConditionalFormat_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId),
    CONSTRAINT UQ_ConditionalFormat_PropertyId_SortOrder UNIQUE (PropertyId, SortOrder)
)
GO

CREATE INDEX IDX_ConditionalFormat_PropertyId ON exp.ConditionalFormat(PropertyId)
GO
