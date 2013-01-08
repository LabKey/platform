/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

/* exp-10.20-10.21.sql */

ALTER TABLE exp.PropertyDescriptor ADD Dimension BIT NOT NULL DEFAULT '0'
GO

ALTER TABLE exp.PropertyDescriptor ADD Measure BIT NOT NULL DEFAULT '0'
GO

UPDATE exp.PropertyDescriptor SET
	Measure = 1
WHERE
		(RangeURI = 'http://www.w3.org/2001/XMLSchema#int' OR
			RangeURI = 'http://www.w3.org/2001/XMLSchema#double') AND
		LookupQuery IS NULL AND
		Hidden = 0  AND
		Name <> 'ParticipantID' AND
		Name <> 'VisitID' AND
		Name <> 'SequenceNum' AND
		Name <> 'RowId'
GO


UPDATE exp.PropertyDescriptor SET
	Dimension = 1
WHERE LookupQuery IS NOT NULL AND
    Hidden = 0 AND
    Name <> 'CreatedBy' AND
    Name <> 'ModifiedBy'
GO

/* exp-10.21-10.22.sql */

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

/* exp-10.22-10.23.sql */

ALTER TABLE exp.domaindescriptor ADD storageTableName NVARCHAR(100);
ALTER TABLE exp.domaindescriptor ADD storageSchemaName NVARCHAR(100);
GO

/* exp-10.23-10.24.sql */

ALTER TABLE exp.Experiment
    ALTER COLUMN hypothesis TEXT
GO

ALTER TABLE exp.Experiment
    ALTER COLUMN comments TEXT
GO

/* exp-10.24-10.25.sql */

ALTER TABLE exp.Material ADD LastIndexed DATETIME
GO