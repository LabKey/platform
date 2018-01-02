/*
 * Copyright (c) 2017 LabKey Corporation
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
EXEC core.fn_dropifexists 'list', 'exp', 'CONSTRAINT', 'DF__list__FileAttachmentIndex';

IF EXISTS( SELECT TOP 1 1 FROM sys.objects o INNER JOIN sys.columns c ON o.object_id = c.object_id WHERE o.name = 'list' AND c.name = 'FileAttachmentIndex')
  ALTER TABLE exp.list DROP COLUMN FileAttachmentIndex
GO

ALTER TABLE exp.list ADD FileAttachmentIndex BIT CONSTRAINT DF__list__FileAttachmentIndex DEFAULT 0 NOT NULL;
GO