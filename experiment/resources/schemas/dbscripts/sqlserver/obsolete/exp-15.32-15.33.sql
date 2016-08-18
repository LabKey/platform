/*
 * Copyright (c) 2016 LabKey Corporation
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

CREATE INDEX IX_Alias_Name ON exp.Alias(Name);

ALTER TABLE exp.DataAliasMap ADD CONSTRAINT FK_DataAlias_LSID FOREIGN KEY (LSID) REFERENCES exp.Data(LSID);
CREATE INDEX IX_DataAliasMap ON exp.DataAliasMap(LSID, Alias, Container);

ALTER TABLE exp.MaterialAliasMap ADD CONSTRAINT FK_MaterialAlias_LSID FOREIGN KEY (LSID) REFERENCES exp.Material(LSID);
CREATE INDEX IX_MaterialAliasMap ON exp.MaterialAliasMap(LSID, Alias, Container);
