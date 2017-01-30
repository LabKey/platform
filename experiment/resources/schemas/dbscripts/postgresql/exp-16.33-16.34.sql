SELECT core.fn_dropifexists('indexinteger', 'exp', 'TABLE', NULL);
SELECT core.fn_dropifexists('indexvarchar', 'exp', 'TABLE', NULL);
SELECT core.fn_dropifexists('list', 'exp', 'CONSTRAINT', 'UQ_RowId');
SELECT core.fn_dropifexists('list', 'exp', 'COLUMN', 'rowid');
