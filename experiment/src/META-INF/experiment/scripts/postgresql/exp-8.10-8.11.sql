/*
 * Copyright (c) 2008 LabKey Corporation
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

-- Clean up duplicate PropertyDescriptor and DomainDescriptors. Two cases:
-- 1. Assay definitions that were deleted before we correctly deleted the domains for the batch, run, and data sets.
-- 2. Duplicate input role domains, cause unknown. At least after the UNIQUE constraints are in place we'll find out if we try to insert dupes again

CREATE TEMPORARY TABLE PropertyIdsToDelete (PropertyId INT);

-- Grab the PropertyIds for properties that belong to assay domains where the assay has been deleted and we have a dupe
INSERT INTO PropertyIdsToDelete (SELECT p.propertyid from exp.propertydescriptor p, exp.propertydomain pd WHERE p.propertyid = pd.propertyid AND pd.domainid IN
		(SELECT domainid FROM exp.domaindescriptor WHERE domainuri LIKE '%:AssayDomain-%'
			AND domainid IN (SELECT DomainId FROM exp.DomainDescriptor WHERE DomainURI IN (SELECT DomainURI FROM (SELECT Count(DomainURI) AS c, DomainURI FROM exp.DomainDescriptor GROUP BY DomainURI) X WHERE c > 1))
			AND domainuri NOT IN
			(SELECT StringValue FROM exp.ObjectProperty op, exp.object o, exp.protocol p WHERE p.lsid = o.objecturi AND op.objectid = o.objectid AND StringValue LIKE '%:AssayDomain-%')));

-- Grab the PropertyIds for duplicate input role domains. We want all the DomainIds except the MAX ones for each DomainURI
INSERT INTO PropertyIdsToDelete (SELECT p.propertyid FROM exp.propertydescriptor p, exp.propertydomain pd WHERE p.propertyid = pd.propertyid AND pd.DomainId IN
	(SELECT DomainId FROM
		exp.DomainDescriptor dd,
		(SELECT COUNT(DomainURI) AS c, MAX(DomainId) as m, DomainURI FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI) x
	WHERE dd.DomainURI = x.DomainURI AND x.c > 1)
AND pd.DomainId NOT IN
	(SELECT MAX(DomainId) as m FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI));

-- Don't try to delete entries that are currently in use
DELETE FROM PropertyIdsToDelete WHERE PropertyId IN (SELECT PropertyId FROM exp.ObjectProperty);

-- Get rid of the duplicate PropertyDescriptors
DELETE FROM exp.PropertyDomain WHERE PropertyId IN (SELECT PropertyId FROM PropertyIdsToDelete);
DELETE FROM exp.PropertyDescriptor WHERE PropertyId IN (SELECT PropertyId FROM PropertyIdsToDelete);

DROP TABLE PropertyIdsToDelete;

-- Get rid of the orphaned assay domains
DELETE FROM exp.DomainDescriptor WHERE DomainId IN (SELECT domainid FROM exp.domaindescriptor WHERE domainuri LIKE '%:AssayDomain-%'
	AND domainid IN (SELECT DomainId FROM exp.DomainDescriptor WHERE DomainURI IN (SELECT DomainURI FROM (SELECT Count(DomainURI) AS c, DomainURI FROM exp.DomainDescriptor GROUP BY DomainURI) X WHERE c > 1))
	AND domainuri NOT IN
	(SELECT StringValue FROM exp.ObjectProperty op, exp.object o, exp.protocol p WHERE p.lsid = o.objecturi AND op.objectid = o.objectid AND StringValue LIKE '%:AssayDomain-%'));

-- Get rid of the duplicate input role domains
DELETE FROM exp.DomainDescriptor WHERE DomainId IN
	(SELECT DomainId FROM
		exp.DomainDescriptor dd,
		(SELECT COUNT(DomainURI) AS c, MAX(DomainId) as m, DomainURI FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI) x
	WHERE dd.DomainURI = x.DomainURI AND x.c > 1)
AND DomainId NOT IN
	(SELECT MAX(DomainId) as m FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI);


-- Add the contraints
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyURIContainer UNIQUE (PropertyURI, Container);
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainURIContainer UNIQUE (DomainURI, Container);
