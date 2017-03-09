/* exp-16.30-16.31.sql */

ALTER TABLE exp.MaterialSource ADD NameExpression NVARCHAR(200) NULL;

/* exp-16.31-16.32.sql */

EXEC core.executeJavaUpgradeCode 'cleanupQuotedAliases';

/* exp-16.32-16.33.sql */

ALTER TABLE exp.material
    ADD description NVARCHAR(4000);

/* exp-16.33-16.34.sql */

EXEC core.fn_dropifexists 'indexinteger', 'exp', 'TABLE';
EXEC core.fn_dropifexists 'indexvarchar', 'exp', 'TABLE';
EXEC core.fn_dropifexists 'list', 'exp', 'CONSTRAINT', 'UQ_RowId';
EXEC core.fn_dropifexists 'list', 'exp', 'COLUMN', 'rowid';