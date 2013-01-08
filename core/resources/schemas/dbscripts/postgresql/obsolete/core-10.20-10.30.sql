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
/* core-10.20-10.21.sql */

CREATE AGGREGATE core.array_accum (anyelement)
(
    sfunc = array_append,
    stype = anyarray,
    initcond = '{}'
);

/* core-10.22-10.23.sql */

CREATE FUNCTION core.sort(anyarray)
RETURNS anyarray AS $$
SELECT ARRAY(SELECT $1[i] from generate_series(array_lower($1,1),
array_upper($1,1)) g(i) ORDER BY 1)
$$ LANGUAGE SQL STRICT IMMUTABLE;