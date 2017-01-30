EXEC core.fn_dropifexists 'indexinteger', 'exp', 'TABLE';
EXEC core.fn_dropifexists 'indexvarchar', 'exp', 'TABLE';
EXEC core.fn_dropifexists 'list', 'exp', 'CONSTRAINT', 'UQ_RowId';
EXEC core.fn_dropifexists 'list', 'exp', 'COLUMN', 'rowid';
