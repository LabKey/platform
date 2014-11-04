/*
 * Copyright (c) 2014 LabKey Corporation
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

--ALTER TABLE study.visit ALTER protocolday DROP NOT NULL;
EXEC core.fn_dropifexists 'Visit', 'study', 'DEFAULT', 'ProtocolDay';
GO
ALTER TABLE study.Visit ALTER COLUMN ProtocolDay NUMERIC(15,4) NULL;
GO
ALTER TABLE study.Visit ADD DEFAULT NULL FOR ProtocolDay;