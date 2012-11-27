/*
 * Copyright (c) 2012 LabKey Corporation
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

/* exp-12.20-12.21.sql */

ALTER TABLE exp.PropertyDescriptor ADD COLUMN Protected BOOLEAN NOT NULL DEFAULT False;

/* exp-12.21-12.22.sql */

-- Add a column to track the chaining of original and replaced runs
ALTER TABLE exp.ExperimentRun ADD COLUMN ReplacedByRunId INT;

ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_ReplacedByRunId FOREIGN KEY (ReplacedByRunId)
        REFERENCES exp.ExperimentRun (RowId);

CREATE INDEX IDX_ExperimentRun_ReplacedByRunId ON exp.ExperimentRun(ReplacedByRunId);

/* exp-12.22-12.23.sql */

ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT uq_domainuricontainer;
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT uq_domaindescriptor;

ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT uq_domaindescriptor UNIQUE (DomainURI, Project);

/* exp-12.23-12.24.sql */

ALTER TABLE exp.RunList ADD COLUMN Created TIMESTAMP;
ALTER TABLE exp.RunList ADD COLUMN CreatedBy INT;

/* exp-12.25-12.26.sql */

ALTER TABLE exp.PropertyDescriptor ALTER COLUMN lookupschema TYPE VARCHAR(200);
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN lookupquery TYPE VARCHAR(200);