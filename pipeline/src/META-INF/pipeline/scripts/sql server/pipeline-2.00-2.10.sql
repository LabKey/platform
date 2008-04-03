exec core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container'
go

-- dropping this index because it seems very nonselective and has been the cause(?) of some deadlocks
exec core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Status'
go

CREATE INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container)
go
