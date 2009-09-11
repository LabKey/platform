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

ALTER TABLE exp.PropertyDescriptor ADD DefaultValueType NVARCHAR(50);

GO

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
)
GO

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
)
GO