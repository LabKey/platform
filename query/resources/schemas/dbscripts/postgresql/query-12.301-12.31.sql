/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

