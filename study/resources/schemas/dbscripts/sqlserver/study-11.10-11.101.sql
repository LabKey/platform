/*
 * Copyright (c) 2011 LabKey Corporation
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

-- NOTE: this script was deployed as a late patch to 11.1. Therefore,
-- we should do 11.2 rollup as 11.101-11.20 in addition to the normal
-- 11.10-11.20.

EXEC core.executeJavaUpgradeCode 'renameObjectIdToRowId'
GO
