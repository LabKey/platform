/*
 * Copyright (c) 2008 LabKey Corporation
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

DELETE FROM pipeline.statusfiles WHERE filepath IN (SELECT filepath FROM (SELECT COUNT(rowid) AS c, filepath FROM pipeline.statusfiles GROUP BY filepath) x WHERE c > 1)
AND rowid NOT IN (SELECT rowid FROM (SELECT COUNT(rowid) AS c, MIN(rowid) AS rowid, filepath FROM pipeline.statusfiles GROUP BY filepath) y WHERE c > 1);

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_FilePath UNIQUE (FilePath);
