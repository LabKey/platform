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

ALTER TABLE exp.PropertyDescriptor ADD COLUMN Dimension BOOLEAN NOT NULL DEFAULT False;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN Measure BOOLEAN NOT NULL DEFAULT False;

UPDATE exp.PropertyDescriptor SET Measure =
		(RangeURI = 'http://www.w3.org/2001/XMLSchema#int' OR RangeURI = 'http://www.w3.org/2001/XMLSchema#double') AND
		LookupQuery IS NULL AND
		NOT Hidden AND
		Name <> 'VisitID' AND
		Name <> 'SequenceNum',
	Dimension = (LookupQuery IS NOT NULL OR Name = 'ParticipantID') AND
		NOT Hidden;