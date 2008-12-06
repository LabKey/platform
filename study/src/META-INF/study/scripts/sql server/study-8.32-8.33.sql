EXEC core.fn_dropifexists 'Visit', 'study', 'INDEX', 'IX_Visit_ContainerSeqNum'
go
EXEC core.fn_dropifexists 'Visit', 'study', 'INDEX', 'IX_Visit_SequenceNumMin'
go
ALTER TABLE study.Visit ADD CONSTRAINT UQ_Visit_ContSeqNum UNIQUE (Container, SequenceNumMin)
go
