/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
/* exp-16.30-16.31.sql */

ALTER TABLE exp.MaterialSource ADD COLUMN NameExpression VARCHAR(200) NULL;

/* exp-16.31-16.32.sql */

SELECT core.executeJavaUpgradeCode('cleanupQuotedAliases');

/* exp-16.32-16.33.sql */

ALTER TABLE exp.material
    ADD description VARCHAR(4000);

/* exp-16.33-16.34.sql */

SELECT core.fn_dropifexists('indexinteger', 'exp', 'TABLE', NULL);
SELECT core.fn_dropifexists('indexvarchar', 'exp', 'TABLE', NULL);
SELECT core.fn_dropifexists('list', 'exp', 'CONSTRAINT', 'UQ_RowId');
SELECT core.fn_dropifexists('list', 'exp', 'COLUMN', 'rowid');