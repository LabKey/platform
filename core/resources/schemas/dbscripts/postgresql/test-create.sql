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
-- PostgreSQL doesn't support synonyms, so create views that match the SQL Server test synonyms.

CREATE VIEW test.TestTable3 AS
    SELECT * FROM test.TestTable;

CREATE VIEW test.Containers2 AS
    SELECT * FROM core.Containers;

CREATE VIEW test.ContainerAliases2 AS
    SELECT * FROM core.ContainerAliases;

CREATE VIEW test.Users2 AS
    SELECT * FROM core.Users;
