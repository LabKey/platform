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

-- When copying to a study, we used to copy the run's created by property as the user's display name. We should
-- copy as the user id with a lookup instead. Migrating takes a few steps

-- First, set the user ids for all of the rows
UPDATE exp.objectproperty SET typetag = 'f', floatvalue =
	(SELECT MAX(userid) FROM
		(SELECT displayname AS name, userid FROM core.users UNION
		 SELECT email AS name, userid from core.users) AS x
	 WHERE stringvalue = name)
WHERE propertyid IN (SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string')
GO

-- Clear out the old string values
UPDATE exp.objectproperty SET stringvalue = NULL
WHERE propertyid IN (SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string')
GO

-- Just to be safe, clean out any ones where we couldn't find the right user
DELETE FROM exp.objectproperty WHERE floatvalue IS NULL AND propertyid IN
(SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string')
GO

-- Update the property descriptor so that it's now an integer and is the correct lookup
UPDATE exp.propertydescriptor SET lookupschema='core', lookupquery='users', rangeuri='http://www.w3.org/2001/XMLSchema#int' WHERE propertyid IN
(SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string')
GO