-- Fix up mismatched containers for documents and announcements. 1.7 code inserted bad containers in some cases.
update core.documents set container = (select a.container from comm.announcements a where a.entityid = core.documents.parent and a.container != core.documents.container) where core.documents.parent IN (select a.entityid from comm.announcements a where a.entityid = core.documents.parent and a.container != core.documents.container)
GO

-- Fix up mismatched containers for documents and announcements. 1.7 code inserted bad containers in some cases.
update core.documents set container = (select p.container from comm.pages p where p.entityid = core.documents.parent and p.container != core.documents.container) where core.documents.parent IN (select p.entityid from comm.pages p where p.entityid = core.documents.parent and p.container != core.documents.container)
GO