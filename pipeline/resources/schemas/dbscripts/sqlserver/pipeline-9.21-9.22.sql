-- drop this index because it is already a unique constraint
-- format of Drop proc
-- exec core.fn_dropifexists <tablename>, <schemaname>, 'INDEX', <indexname>
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Job'
go
-- drop this index because we can change the code to always pass container with jobparent
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_JobParent'
go
-- drop this index so we can add the jobparent column to it
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container'
go
-- in case this has alreaady been run, drop the new index before tryint to create it
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container_JobParent'
go

CREATE CLUSTERED INDEX IX_StatusFiles_Container_JobParent ON pipeline.StatusFiles 
(Container ASC, JobParent ASC)
go
