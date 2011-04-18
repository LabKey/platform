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

-- Specifies the tables to expose in a schema:
--  Comma-separated list of table names specifies a subset of tables in the schema
--  '*' represents all tables
--  Empty represents no tables (not very useful, of course...)
ALTER TABLE query.ExternalSchema ADD
    Tables VARCHAR(8000) NOT NULL DEFAULT '*';
