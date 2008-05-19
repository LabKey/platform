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
-- Fix up mismatched containers for documents and announcements. 1.7 code inserted bad containers in some cases.
update core.documents set container = (select a.container from comm.announcements a where a.entityid = core.documents.parent and a.container != core.documents.container) where core.documents.parent IN (select a.entityid from comm.announcements a where a.entityid = core.documents.parent and a.container != core.documents.container)
GO

-- Fix up mismatched containers for documents and announcements. 1.7 code inserted bad containers in some cases.
update core.documents set container = (select p.container from comm.pages p where p.entityid = core.documents.parent and p.container != core.documents.container) where core.documents.parent IN (select p.entityid from comm.pages p where p.entityid = core.documents.parent and p.container != core.documents.container)
GO