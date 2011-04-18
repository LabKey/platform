/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

/* exp-8.30-8.31.sql */

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

/* exp-8.31-8.32.sql */

ALTER TABLE exp.Experiment ADD COLUMN Hidden BOOLEAN NOT NULL DEFAULT '0';

/* exp-8.32-8.33.sql */

ALTER TABLE exp.ObjectProperty ADD COLUMN QcValue VARCHAR(50);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN QcEnabled BOOLEAN NOT NULL DEFAULT '0';

/* exp-8.34-8.35.sql */

ALTER TABLE exp.materialsource ADD COLUMN ParentCol VARCHAR(200) NULL;

/* exp-8.35-8.36.sql */

ALTER TABLE exp.Experiment ADD COLUMN
    BatchProtocolId int NULL;

ALTER TABLE exp.Experiment ADD CONSTRAINT
    FK_Experiment_BatchProtocolId FOREIGN KEY (BatchProtocolId) REFERENCES exp.Protocol (RowId);

CREATE INDEX IDX_Experiment_BatchProtocolId ON exp.Experiment(BatchProtocolId);

/* exp-8.36-8.37.sql */

ALTER TABLE exp.PropertyDescriptor ADD DefaultValueType VARCHAR(50);

-- Set default value type to LAST_ENTERED for all non-data assay domain properties EXCEPT those
-- that we know should be entered every time :
UPDATE exp.PropertyDescriptor SET DefaultValueType = 'LAST_ENTERED' WHERE PropertyId IN
(
    SELECT exp.PropertyDescriptor.propertyid FROM exp.PropertyDescriptor
    JOIN exp.PropertyDomain ON
        exp.PropertyDescriptor.PropertyId = exp.PropertyDomain.PropertyId
    JOIN exp.DomainDescriptor ON
        exp.PropertyDomain.DomainId = exp.DomainDescriptor.DomainId
    -- get all assay domains except data domains:
    WHERE exp.DomainDescriptor.DomainURI LIKE '%:AssayDomain-%' AND
    exp.DomainDescriptor.DomainURI NOT LIKE '%:AssayDomain-Data%' AND NOT
        -- first, check for these properties in sample well group domains:
          ( exp.PropertyDescriptor.PropertyURI LIKE '%#SpecimenID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#VisitID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#ParticipantID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#Date')
);

-- Set default value type to LAST_ENTERED for all non-data assay domain properties EXCEPT those
-- that we know should be entered every time:
UPDATE exp.PropertyDescriptor SET DefaultValueType = 'FIXED_EDITABLE' WHERE PropertyId IN
(
    SELECT exp.PropertyDescriptor.propertyid FROM exp.PropertyDescriptor
    JOIN exp.PropertyDomain ON
        exp.PropertyDescriptor.PropertyId = exp.PropertyDomain.PropertyId
    JOIN exp.DomainDescriptor ON
        exp.PropertyDomain.DomainId = exp.DomainDescriptor.DomainId
    -- get all assay domains except data domains:
    WHERE exp.DomainDescriptor.DomainURI LIKE '%:AssayDomain-%' AND
    exp.DomainDescriptor.DomainURI NOT LIKE '%:AssayDomain-Data%' AND
        -- first, check for these properties in sample well group domains:
          ( exp.PropertyDescriptor.PropertyURI LIKE '%#SpecimenID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#VisitID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#ParticipantID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#Date')
);

/* exp-8.37-8.38.sql */

ALTER TABLE exp.PropertyDescriptor ADD COLUMN Hidden BOOLEAN NOT NULL DEFAULT '0';