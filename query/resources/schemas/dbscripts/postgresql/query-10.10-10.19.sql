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

/* query-10.10-10.11.sql */

-- Rename table and column to use "external schema" terminology
ALTER TABLE query.DbUserSchema RENAME TO ExternalSchema;

ALTER TABLE query.ExternalSchema
    RENAME DbUserSchemaId TO ExternalSchemaId;

-- Add bit to determine whether to index or not (indexing is on by default)
ALTER TABLE query.ExternalSchema ADD
    COLUMN Indexable BOOLEAN NOT NULL DEFAULT TRUE;

/* query-10.11-10.12.sql */

-- Specifies the tables to expose in a schema:
--  Comma-separated list of table names specifies a subset of tables in the schema
--  '*' represents all tables
--  Empty represents no tables (not very useful, of course...)
ALTER TABLE query.ExternalSchema ADD
    COLUMN Tables VARCHAR(8000) NOT NULL DEFAULT '*';

/* query-10.12-10.13.sql */

-- Switch to case-insensitive unique index... and rename it to match the new table name 
ALTER TABLE query.ExternalSchema DROP CONSTRAINT UQ_DbUserSchema;
CREATE UNIQUE INDEX UQ_ExternalSchema ON query.ExternalSchema (Container, LOWER(UserSchemaName));