/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

/* list-13.10-13.20.sql */

CREATE SCHEMA list;

SELECT core.fn_dropifexists('indexinteger', 'exp', 'TABLE', NULL);
SELECT core.fn_dropifexists('indexvarchar', 'exp', 'TABLE', NULL);
SELECT core.fn_dropifexists('list', 'exp', 'CONSTRAINT', 'UQ_RowId');

ALTER TABLE exp.list DROP COLUMN IF EXISTS rowid CASCADE;
