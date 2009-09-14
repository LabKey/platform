/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

-- Migrate from storing roles as property descriptors to storing them as strings on the MaterialInput
ALTER TABLE exp.MaterialInput ADD COLUMN Role VARCHAR(50);
UPDATE exp.MaterialInput SET Role =
    (SELECT pd.Name FROM exp.PropertyDescriptor pd WHERE pd.PropertyId = exp.MaterialInput.PropertyId);
UPDATE exp.MaterialInput SET Role = 'Material' WHERE Role IS NULL;
ALTER TABLE exp.MaterialInput ALTER COLUMN Role SET NOT NULL;
CREATE INDEX IDX_MaterialInput_Role ON exp.MaterialInput(Role);

ALTER TABLE exp.MaterialInput DROP COLUMN PropertyId;


-- Migrate from storing roles as property descriptors to storing them as strings on the DataInput
ALTER TABLE exp.DataInput ADD COLUMN Role VARCHAR(50);
UPDATE exp.DataInput SET Role =
    (SELECT pd.Name FROM exp.PropertyDescriptor pd WHERE pd.PropertyId = exp.DataInput.PropertyId);
UPDATE exp.DataInput SET Role = 'Data' WHERE Role IS NULL;
ALTER TABLE exp.DataInput ALTER COLUMN Role SET NOT NULL;
CREATE INDEX IDX_DataInput_Role ON exp.DataInput(Role);

ALTER TABLE exp.DataInput DROP COLUMN PropertyId;

-- Clean up the domain and property descriptors for input roles
DELETE FROM exp.PropertyDomain WHERE
    DomainId IN (SELECT DomainId FROM exp.DomainDescriptor WHERE
                    DomainURI LIKE '%:Domain.Folder-%:MaterialInputRole' OR
                    DomainURI LIKE '%:Domain.Folder-%:DataInputRole')
    OR PropertyId IN (SELECT PropertyId FROM exp.PropertyDescriptor WHERE
                    PropertyURI LIKE '%:Domain.Folder-%:MaterialInputRole#%' OR
                    PropertyURI LIKE '%:Domain.Folder-%:DataInputRole#%');

DELETE FROM exp.DomainDescriptor WHERE
    DomainURI LIKE '%:Domain.Folder-%:MaterialInputRole' OR
    DomainURI LIKE '%:Domain.Folder-%:DataInputRole';

DELETE FROM exp.PropertyDescriptor WHERE
    PropertyURI LIKE '%:Domain.Folder-%:MaterialInputRole#%' OR
    PropertyURI LIKE '%:Domain.Folder-%:DataInputRole#%';