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
-- Fix up sample sets that were incorrectly created in a folder other than their domain
-- https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=4976

update exp.materialsource set container = (select dd.container from exp.domaindescriptor dd where exp.materialsource.lsid = dd.domainuri)
where rowid in (select ms.rowid from exp.materialsource ms, exp.domaindescriptor dd where dd.domainuri = ms.lsid and ms.container != dd.container)
GO
