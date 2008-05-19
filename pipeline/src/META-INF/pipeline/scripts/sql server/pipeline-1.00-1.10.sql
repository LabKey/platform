/*
 * Copyright (c) 2005-2008 LabKey Corporation
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


ALTER TABLE pipeline.StatusFiles ADD
    Description NVARCHAR(255),
    DataUrl NVARCHAR(255),
    Job uniqueidentifier,
    Provider NVARCHAR(255)
GO

UPDATE pipeline.StatusFiles SET Provider = 'X!Tandem (Cluster)'
WHERE FilePath like '%/xtan/%'

UPDATE pipeline.StatusFiles SET Provider = 'Comet (Cluster)'
WHERE FilePath like '%/cmt/%'

UPDATE pipeline.StatusFiles SET Provider = 'msInspect (Cluster)'
WHERE FilePath like '%/inspect/%'
GO
