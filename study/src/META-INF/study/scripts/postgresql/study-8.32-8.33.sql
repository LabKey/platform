SELECT core.fn_dropifexists('Visit', 'study', 'INDEX', 'IX_Visit_ContainerSeqNum')
;
SELECT core.fn_dropifexists('Visit', 'study', 'INDEX', 'IX_Visit_SequenceNumMin')
;
ALTER TABLE study.Visit ADD CONSTRAINT UQ_Visit_ContSeqNum UNIQUE(Container, SequenceNumMin)
;
