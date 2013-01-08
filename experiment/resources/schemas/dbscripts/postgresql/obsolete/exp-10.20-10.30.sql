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

ALTER TABLE exp.PropertyDescriptor ADD COLUMN Dimension BOOLEAN NOT NULL DEFAULT False;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN Measure BOOLEAN NOT NULL DEFAULT False;

UPDATE exp.PropertyDescriptor SET Measure =
		(RangeURI = 'http://www.w3.org/2001/XMLSchema#int' OR RangeURI = 'http://www.w3.org/2001/XMLSchema#double') AND
		LookupQuery IS NULL AND
		NOT Hidden AND
		Name <> 'ParticipantId' AND
		Name <> 'VisitID' AND
		Name <> 'SequenceNum' AND
		Name <> 'RowId',
	Dimension = LookupQuery IS NOT NULL AND
	    NOT Hidden AND
	    Name <> 'CreatedBy' AND
	    Name <> 'ModifiedBy';

/* exp-10.21-10.22.sql */

CREATE TABLE exp.ConditionalFormat (
    RowId SERIAL NOT NULL,
    PropertyId INT NOT NULL,
    SortOrder INT NOT NULL,
    Filter VARCHAR(500) NOT NULL,
    Bold BOOLEAN NOT NULL,
    Italic BOOLEAN NOT NULL,
    Strikethrough BOOLEAN NOT NULL,
    TextColor VARCHAR(10),
    BackgroundColor VARCHAR(10),

    CONSTRAINT PK_ConditionalFormat_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_ConditionalFormat_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId),
    CONSTRAINT UQ_ConditionalFormat_PropertyId_SortOrder UNIQUE (PropertyId, SortOrder)
);

CREATE INDEX IDX_ConditionalFormat_PropertyId ON exp.ConditionalFormat(PropertyId);

/* exp-10.22-10.23.sql */

ALTER TABLE exp.domaindescriptor ADD COLUMN storageTableName character varying(100);
ALTER TABLE exp.domaindescriptor ADD COLUMN storageSchemaName character varying(100);

/* exp-10.23-10.24.sql */

ALTER TABLE exp.Experiment
    ALTER COLUMN hypothesis TYPE TEXT;

ALTER TABLE exp.Experiment
    ALTER COLUMN comments TYPE TEXT;

/* exp-10.24-10.25.sql */

ALTER TABLE exp.Material ADD COLUMN LastIndexed TIMESTAMP;