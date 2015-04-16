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
    ADD COLUMN SchemaType VARCHAR(50) NOT NULL DEFAULT 'external';

ALTER TABLE query.ExternalSchema
    ADD COLUMN SchemaTemplate VARCHAR(50);

ALTER TABLE query.ExternalSchema
    RENAME DbSchemaName TO SourceSchemaName;

ALTER TABLE query.ExternalSchema
    ALTER COLUMN SourceSchemaName DROP NOT NULL;

-- SchemaTemplate is not compatible with the SourceSchemaName, Tables, and Metadata columns
ALTER TABLE query.ExternalSchema
    ADD CONSTRAINT "CK_SchemaTemplate"
    CHECK ((SchemaTemplate IS NULL     AND SourceSchemaName IS NOT NULL AND Tables IS NOT NULL) OR
           (SchemaTemplate IS NOT NULL AND SourceSchemaName IS NULL     AND Tables IS NULL     AND MetaData IS NULL));

/* query-12.31-12.32.sql */

-- Remove default '*' and allow null Tables column
ALTER TABLE query.ExternalSchema ALTER COLUMN Tables DROP DEFAULT;
ALTER TABLE query.ExternalSchema ALTER COLUMN Tables DROP NOT NULL;

/* query-12.32-12.33.sql */

-- Also checked in as query-12.30-12.301 since it's safe to rerun.
-- BE SURE TO CONSOLIDATE QUERY MODULE SCRIPTS STARTING WITH 12.301 for the 13.1 release.

ALTER TABLE query.customview ALTER COLUMN schema TYPE VARCHAR(200);

/* query-12.33-12.34.sql */

-- Remove check constraint to allow overriding the schema template,
-- but continue to require NOT NULL SourceSchemaName and Tables when SchemaTemplate IS NULL.
ALTER TABLE query.ExternalSchema
   DROP CONSTRAINT "CK_SchemaTemplate";

ALTER TABLE query.ExternalSchema
    ADD CONSTRAINT "CK_SchemaTemplate"
    CHECK (SchemaTemplate IS NOT NULL OR (SchemaTemplate IS NULL AND SourceSchemaName IS NOT NULL AND Tables IS NOT NULL));