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

ALTER TABLE exp.ObjectProperty ADD MvIndicator NVARCHAR(50) NULL
GO

UPDATE exp.ObjectProperty SET MvIndicator = QcValue
GO

ALTER TABLE exp.ObjectProperty DROP COLUMN QcValue
GO

ALTER TABLE exp.PropertyDescriptor ADD MvEnabled BIT NOT NULL DEFAULT 0
GO

UPDATE exp.PropertyDescriptor SET MvEnabled = QcEnabled
GO

declare @constname sysname
select @constname= so.name
from
sysobjects so inner join sysconstraints sc on (sc.constid = so.id)
inner join sysobjects soc on (sc.id = soc.id)
where so.xtype='D'
and soc.id=object_id('exp.PropertyDescriptor')
and col_name(soc.id, sc.colid) = 'QcEnabled'

declare @cmd varchar(500)
select @cmd='Alter Table exp.PropertyDescriptor DROP CONSTRAINT ' + @constname
select @cmd

exec(@cmd)

ALTER TABLE exp.PropertyDescriptor DROP COLUMN QcEnabled
GO
