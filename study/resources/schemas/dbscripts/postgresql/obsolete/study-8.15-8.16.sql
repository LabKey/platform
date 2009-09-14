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
UPDATE study.Study
SET DatasetRowsEditable = FALSE
WHERE
DatasetRowsEditable IS NULL;

UPDATE study.Dataset
SET KeyPropertyManaged = FALSE
WHERE
KeyPropertyManaged IS NULL;

ALTER TABLE study.Study
    ALTER COLUMN DatasetRowsEditable SET NOT NULL;

ALTER TABLE study.Dataset
    ALTER COLUMN KeyPropertyManaged SET NOT NULL;
