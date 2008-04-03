SELECT core.fn_dropifexists ('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container')
;

-- dropping this index because it seems very nonselective and has been the cause(?) of some deadlocks
SELECT core.fn_dropifexists ('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Status')
;

CREATE INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container)
;
