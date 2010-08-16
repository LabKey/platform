/*
 * Copyright (c) 2010 LabKey Corporation
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

ALTER TABLE exp.PropertyDescriptor ADD Dimension BIT NOT NULL DEFAULT '0'
GO

ALTER TABLE exp.PropertyDescriptor ADD Measure BIT NOT NULL DEFAULT '0'
GO

UPDATE exp.PropertyDescriptor SET
	Measure = 1
WHERE
		(RangeURI = 'http://www.w3.org/2001/XMLSchema#int' OR
			RangeURI = 'http://www.w3.org/2001/XMLSchema#double') AND
		LookupQuery IS NULL AND
		Hidden = 0  AND
		Name <> 'ParticipantID' AND
		Name <> 'VisitID' AND
		Name <> 'SequenceNum'
GO


UPDATE exp.PropertyDescriptor SET
	Dimension = 1
WHERE (LookupQuery IS NOT NULL OR Name = 'ParticipantID') AND Hidden = 0
GO