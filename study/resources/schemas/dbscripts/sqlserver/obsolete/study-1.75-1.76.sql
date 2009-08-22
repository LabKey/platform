/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
UPDATE exp.ObjectProperty SET
    StringValue = CAST(exp.ObjectProperty.floatValue AS INTEGER),
    TypeTag = 's',
    floatValue = NULL
WHERE
    (SELECT exp.PropertyDescriptor.PropertyURI FROM exp.PropertyDescriptor
        WHERE exp.PropertyDescriptor.PropertyId =
        exp.ObjectProperty.PropertyId) LIKE '%StudyDataset.%NAB#FileId' AND
    (SELECT exp.PropertyDescriptor.RangeURI FROM exp.PropertyDescriptor
        WHERE exp.PropertyDescriptor.PropertyId = exp.ObjectProperty.PropertyId) =
        'http://www.w3.org/2001/XMLSchema#int';

UPDATE exp.PropertyDescriptor SET
    RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
WHERE
    exp.PropertyDescriptor.RangeURI = 'http://www.w3.org/2001/XMLSchema#int' AND
    exp.PropertyDescriptor.PropertyURI LIKE '%StudyDataset.%NAB#FileId';