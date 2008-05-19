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
exec core.fn_dropifexists 'materialsource', 'cabig','VIEW', NULL
GO

DROP VIEW exp.MaterialSourceWithProject
GO

ALTER TABLE exp.materialsource ADD
    IdCol1 NVARCHAR(200) NULL,
    IdCol2 NVARCHAR(200) NULL,
    IdCol3 NVARCHAR(200) NULL
GO

CREATE VIEW exp.MaterialSourceWithProject AS
    SELECT ms.RowId, ms.Name, ms.LSID, ms.MaterialLSIDPrefix, ms.Description,
        ms.Created,	ms.CreatedBy, ms.Modified, ms.ModifiedBy, ms.Container , dd.Project, ms.IdCol1, ms.IdCol2, ms.IdCol3
    FROM exp.MaterialSource ms
    LEFT OUTER JOIN exp.DomainDescriptor dd ON ms.lsid = dd.domainuri
GO

ALTER TABLE exp.list ADD
    DiscussionSetting SMALLINT NOT NULL DEFAULT 0,
    AllowDelete BIT NOT NULL DEFAULT 1,
    AllowUpload BIT NOT NULL DEFAULT 1,
    AllowExport BIT NOT NULL DEFAULT 1
GO

-- Used to attach discussions to lists
ALTER TABLE exp.IndexInteger ADD
    EntityId ENTITYID
GO

ALTER TABLE exp.IndexVarchar ADD
    EntityId ENTITYID
GO
