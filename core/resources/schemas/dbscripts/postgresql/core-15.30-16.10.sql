/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

/* core-15.30-15.31.sql */

-- No longer used; replaced by built-in PostgreSQL function string_to_array()
DROP AGGREGATE core.array_accum(anyelement);
DROP AGGREGATE core.array_accum(text);

/* core-15.31-15.32.sql */

SELECT core.fn_dropifexists('bulkImport', 'core', 'FUNCTION', 'text, text, text');

-- An empty stored procedure (similar to executeJavaUpgradeCode) that, when detected by the script runner,
-- imports a tabular data file (TSV, XLSX, etc.) into the specified table.
CREATE FUNCTION core.bulkImport(text, text, text, boolean = false) RETURNS void AS $$
DECLARE note TEXT := 'Empty function that signals script runner to bulk import a file into a table.';
BEGIN
END
$$ LANGUAGE plpgsql;