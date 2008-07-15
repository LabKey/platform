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
