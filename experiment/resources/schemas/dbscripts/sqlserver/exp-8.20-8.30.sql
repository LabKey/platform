/*
 * Copyright (c) 2008 LabKey Corporation
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
/* exp-8.20-8.21.sql */

CREATE TABLE exp.PropertyValidator (
    RowId INT IDENTITY(1,1) NOT NULL,
    Name NVARCHAR(50) NOT NULL,
    Description NVARCHAR(200),
    TypeURI NVARCHAR(200) NOT NULL,
    Expression TEXT,
    Properties TEXT,
    ErrorMessage TEXT,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId)
);
GO

CREATE TABLE exp.ValidatorReference (
    ValidatorId INT NOT NULL,
    PropertyId INT NOT NULL,

    CONSTRAINT PK_ValidatorReference PRIMARY KEY (ValidatorId, PropertyId),
    CONSTRAINT FK_PropertyValidator_ValidatorId FOREIGN KEY (ValidatorId) REFERENCES exp.PropertyValidator (RowId),
    CONSTRAINT FK_PropertyDescriptor_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
);
GO

/* exp-8.21-8.22.sql */

-- Change StringValue from NVARCHAR(4000) to NTEXT
ALTER TABLE exp.ProtocolApplicationParameter
    ALTER COLUMN StringValue NTEXT NULL
GO

/* exp-8.23-8.24.sql */

EXEC core.executeJavaUpgradeCode 'version132Upgrade'
GO

UPDATE exp.protocolapplication SET cpastype = 'ProtocolApplication' WHERE
    cpastype != 'ProtocolApplication' AND
    cpastype != 'ExperimentRun' AND
    cpastype != 'ExperimentRunOutput'
GO