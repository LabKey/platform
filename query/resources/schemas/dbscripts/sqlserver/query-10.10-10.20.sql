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
EXEC sp_rename 'query.DbUserSchema', 'ExternalSchema'
GO

EXEC sp_rename 'query.ExternalSchema.DbUserSchemaId', 'ExternalSchemaId', 'COLUMN'
GO

-- Add bit to determine whether to index or not (indexing is on by default)
ALTER TABLE query.ExternalSchema ADD
    Indexable BIT NOT NULL DEFAULT 1
GO

/* query-10.11-10.12.sql */

-- Specifies the tables to expose in a schema:
--  Comma-separated list of table names specifies a subset of tables in the schema
--  '*' represents all tables
--  Empty represents no tables (not very useful, of course...)
ALTER TABLE query.ExternalSchema ADD
    Tables VARCHAR(8000) NOT NULL DEFAULT '*';

/* query-10.12-10.13.sql */

-- Rename constraint so it matches new PostgreSQL index name
EXEC sp_rename 'query.UQ_DbUserSchema', 'UQ_ExternalSchema', 'OBJECT'
GO

/* query-10.13-10.14.sql */

-- Fix SQL Server-only issue -- old schema defs need editable set to default value and then change column to NOT NULL
UPDATE query.ExternalSchema SET Editable = 0 WHERE Editable IS NULL
GO

ALTER TABLE query.ExternalSchema
    ALTER COLUMN Editable BIT NOT NULL
GO