/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

/* query-12.301-12.31.sql */

ALTER TABLE query.ExternalSchema
    ADD SchemaType NVARCHAR(50) NOT NULL CONSTRAINT DF_ExternalSchema_SchemaType DEFAULT 'external';

ALTER TABLE query.ExternalSchema
    ADD SchemaTemplate NVARCHAR(50);

EXECUTE sp_rename N'query.ExternalSchema.DbSchemaName', N'SourceSchemaName', 'COLUMN';

ALTER TABLE query.ExternalSchema
    ALTER COLUMN SourceSchemaName NVARCHAR(50) NULL;

GO

-- SchemaTemplate is not compatible with the SourceSchemaName, Tables, and Metadata columns
-- SQLServer doesn't allow CHECK constraints on NTEXT columns so we can't check that MetaData IS NULL when SchemaTemplate IS NOT NULL.
ALTER TABLE query.ExternalSchema
    ADD CONSTRAINT "CK_SchemaTemplate"
    CHECK ((SchemaTemplate IS NULL     AND SourceSchemaName IS NOT NULL AND Tables IS NOT NULL) OR
           (SchemaTemplate IS NOT NULL AND SourceSchemaName IS NULL     AND Tables IS NULL    ))

/* query-12.31-12.32.sql */

-- Remove default '*' and allow null Tables column
EXEC core.fn_dropifexists 'ExternalSchema', 'query', 'DEFAULT', 'Tables';
ALTER TABLE query.ExternalSchema ALTER COLUMN Tables varchar(8000) NULL;

/* query-12.32-12.33.sql */

-- Also checked in as query-12.30-12.301 since it's safe to rerun.
-- BE SURE TO CONSOLIDATE QUERY MODULE SCRIPTS STARTING WITH 12.301 for the 13.1 release.

ALTER TABLE query.customview ALTER COLUMN "schema" NVARCHAR(200);

/* query-12.33-12.34.sql */

-- Remove check constraint to allow overriding the schema template,
-- but continue to require NOT NULL SourceSchemaName and Tables when SchemaTemplate IS NULL.
ALTER TABLE query.ExternalSchema
   DROP CONSTRAINT "CK_SchemaTemplate";

GO

ALTER TABLE query.ExternalSchema
    ADD CONSTRAINT "CK_SchemaTemplate"
    CHECK (SchemaTemplate IS NOT NULL OR (SchemaTemplate IS NULL AND SourceSchemaName IS NOT NULL AND Tables IS NOT NULL));