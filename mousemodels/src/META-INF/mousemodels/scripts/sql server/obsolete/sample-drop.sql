use core
go

drop database sample
go

delete containers where name = '_mouselookups'
delete modules where name = 'MouseModels'
delete SqlScripts where ModuleName='MouseModels'
go