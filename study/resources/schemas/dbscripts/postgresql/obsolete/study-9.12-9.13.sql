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

-- It was a mistake to make this a "hard" foreign key- query will take care of
-- linking without it, so we just need an index.  This allows us to drop all contents
-- of the table when reloading.
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives2;

CREATE INDEX IX_Specimens_Derivatives2 ON study.Specimen(DerivativeTypeId2);