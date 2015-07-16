/*
 * Copyright (c) 2015 LabKey Corporation
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
-- These are used to test several different synonym scenarios

CREATE SYNONYM test.TestTable3 FOR test.TestTable;                  -- Table in test schema
CREATE SYNONYM test.Containers2 FOR core.Containers;                -- Table in another schema, with an FK to itself
CREATE SYNONYM test.ContainerAliases2 FOR core.ContainerAliases;    -- Table in another schema, with an FK to the synonym above
CREATE SYNONYM test.Users2 FOR core.Users;                          -- View in another schema
