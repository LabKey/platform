if exists (select * from sysobjects where id = OBJECT_ID('proc_DropConstraint')) 
	drop proc proc_DropConstraint
go

create proc proc_DropConstraint
(@schema sysname, @table sysname, @constprop nvarchar(30))
as

declare @constname sysname
select @constname = so.name
-- a comment
from dbo.sysobjects so 
	inner join dbo.sysconstraints sc on (so.id = sc.constid)
	inner join dbo.sysobjects sou on (sc.id = sou.id )
where USER_NAME(sou.uid) = @schema AND OBJECT_NAME(sc.id) = @table AND OBJECTPROPERTY(so.id, @constprop) = 1;
if @constname is not null
begin 
declare @cmd nvarchar(500) 
set @cmd = 'ALTER TABLE ' + @schema + '.' + @table + ' DROP CONSTRAINT ' + @constname
select @cmd
exec(@cmd)
end

go
exec proc_DropConstraint N'exp', N'DataInput', N'IsPrimaryKey'
go

ALTER TABLE exp.DataInput 
	ADD CONSTRAINT PK_DataInput PRIMARY KEY (DataId,TargetApplicationId)   
GO

DROP INDEX exp.ExperimentRun.IDX_CL_ExperimentRun_ProtocolLSID
GO
DROP INDEX exp.ExperimentRun.IDX_ExperimentRun_ExperimentLSID 
GO
CREATE INDEX IDX_CL_ExperimentRun_ExperimentLSID ON exp.ExperimentRun(ExperimentLSID)
GO

DROP INDEX exp.Data.IDX_Data_RunId
GO
DROP INDEX exp.Data.IDX_CL_Data_SourceProtocolLSID 
GO
CREATE CLUSTERED INDEX IDX_CL_Data_RunId ON exp.Data(RunId)
GO

DROP INDEX exp.Material.IDX_Material_RunId
GO
DROP INDEX exp.Material.IDX_CL_Material_SourceProtocolLSID 
GO
CREATE CLUSTERED INDEX IDX_CL_Material_RunId ON exp.Material(RunId)
GO

DROP INDEX exp.ProtocolApplication.IDX_ProtocolApplication_RunId
GO
DROP INDEX exp.ProtocolApplication.IDX_CL_ProtocolApplication_ProtocolLSID 
GO
CREATE CLUSTERED INDEX IDX_CL_ProtocolApplication_RunId ON exp.ProtocolApplication(RunId)
GO
CREATE INDEX IDX_ProtocolApplication_ProtocolLSID ON exp.ProtocolApplication(ProtocolLSID)
GO


