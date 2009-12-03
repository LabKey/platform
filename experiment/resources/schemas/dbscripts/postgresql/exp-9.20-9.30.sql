/*
 * Copyright (c) 2009 LabKey Corporation
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

/* exp-9.21-9.22.sql */

ALTER TABLE exp.PropertyDomain ADD COLUMN SortOrder INT NOT NULL DEFAULT 0;

-- Set the initial sort to be the same as it has thus far
UPDATE exp.PropertyDomain SET SortOrder = PropertyId;

/* exp-9.22-9.23.sql */

ALTER TABLE exp.PropertyDescriptor ADD COLUMN ImportAliases VARCHAR(200);

/* exp-9.24-9.25.sql */

ALTER TABLE exp.PropertyDescriptor ADD COLUMN URL VARCHAR(200);

/* exp-9.25-9.26.sql */

-- remove property validator dependency on LSID authority
UPDATE exp.propertyvalidator SET typeuri = 'urn:lsid:labkey.com:' ||
    (CASE WHEN strpos(typeuri, 'PropertyValidator:range') = 0
        THEN 'PropertyValidator:regex'
        ELSE 'PropertyValidator:range' END)
  WHERE typeuri NOT LIKE 'urn:lsid:labkey.com:%';

/* exp-9.26-9.27.sql */

ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInInsertView BOOLEAN NOT NULL DEFAULT '1';
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInUpdateView BOOLEAN NOT NULL DEFAULT '1';
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInDetailsView BOOLEAN NOT NULL DEFAULT '1';