-- drop this index because it is already a unique constraint
-- format of Drop proc
-- select core.fn_dropifexists(<tablename>, <schemaname>, 'INDEX', <indexname>
select core.fn_dropifexists('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Job')
;
-- drop this index because we can change the code to always pass container with jobparent
select core.fn_dropifexists('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_JobParent')
;
-- drop this index so we can add the jobparent column to it
select core.fn_dropifexists('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container')
;

-- in case this has alreaady been run, drop the new index before tryint to create it
select core.fn_dropifexists('StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container_JobParent')
;

CREATE INDEX IX_StatusFiles_Container_JobParent ON pipeline.StatusFiles 
(Container, JobParent)
;
