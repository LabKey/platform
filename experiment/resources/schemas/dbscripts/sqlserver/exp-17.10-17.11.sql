EXEC core.fn_dropifexists 'list', 'exp', 'CONSTRAINT', 'DF__list__FileAttachmentIndex';

IF EXISTS( SELECT TOP 1 1 FROM sys.objects o INNER JOIN sys.columns c ON o.object_id = c.object_id WHERE o.name = 'list' AND c.name = 'FileAttachmentIndex')
  ALTER TABLE exp.list DROP COLUMN FileAttachmentIndex
GO

ALTER TABLE exp.list ADD FileAttachmentIndex BIT CONSTRAINT DF__list__FileAttachmentIndex DEFAULT 0 NOT NULL;
GO