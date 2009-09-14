/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
ALTER TABLE study.Study
    ADD SecurityType NVARCHAR(32)
GO

UPDATE study.Study
  SET SecurityType = 'ADVANCED'
  WHERE
  StudySecurity = 1

UPDATE study.Study
  SET SecurityType = 'EDITABLE_DATASETS'
  WHERE
  StudySecurity = 0 AND
  DatasetRowsEditable = 1

UPDATE study.Study
  SET SecurityType = 'BASIC'
  WHERE
  StudySecurity = 0 AND
  DatasetRowsEditable = 0

GO

declare @constname sysname
select @constname= so.name
from
sysobjects so inner join sysconstraints sc on (sc.constid = so.id)
inner join sysobjects soc on (sc.id = soc.id)
where so.xtype='D'
and soc.id=object_id('Study.Study')
and col_name(soc.id, sc.colid) = 'StudySecurity'

declare @cmd varchar(500)
select @cmd='Alter Table Study.Study DROP CONSTRAINT ' + @constname
select @cmd

exec(@cmd)

ALTER TABLE study.Study
  DROP COLUMN StudySecurity

select @constname= so.name
from
sysobjects so inner join sysconstraints sc on (sc.constid = so.id)
inner join sysobjects soc on (sc.id = soc.id)
where so.xtype='D'
and soc.id=object_id('Study.Study')
and col_name(soc.id, sc.colid) = 'DatasetRowsEditable'

select @cmd='Alter Table Study.Study DROP CONSTRAINT ' + @constname
select @cmd

exec(@cmd)

ALTER TABLE study.Study
  DROP COLUMN DatasetRowsEditable

GO  

ALTER TABLE study.Study
  ALTER COLUMN SecurityType NVARCHAR(32) NOT NULL

GO  
