/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
    RowId SERIAL NOT NULL,
    Name VARCHAR(50) NOT NULL,
    Description VARCHAR(200),
    TypeURI VARCHAR(200) NOT NULL,
    Expression TEXT,
    Properties TEXT,
    ErrorMessage TEXT,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId)
);

CREATE TABLE exp.ValidatorReference (
    ValidatorId INT NOT NULL,
    PropertyId INT NOT NULL,

    CONSTRAINT PK_ValidatorReference PRIMARY KEY (ValidatorId, PropertyId),
    CONSTRAINT FK_PropertyValidator_ValidatorId FOREIGN KEY (ValidatorId) REFERENCES exp.PropertyValidator (RowId),
    CONSTRAINT FK_PropertyDescriptor_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
);

/* exp-8.21-8.22.sql */

-- Change StringValue from VARCHAR(4000) to TEXT
ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue TYPE TEXT;

/* exp-8.23-8.24.sql */

SELECT core.executeJavaUpgradeCode('version132Upgrade');

UPDATE exp.protocolapplication SET cpastype = 'ProtocolApplication' WHERE
    cpastype != 'ProtocolApplication' AND
    cpastype != 'ExperimentRun' AND
    cpastype != 'ExperimentRunOutput';