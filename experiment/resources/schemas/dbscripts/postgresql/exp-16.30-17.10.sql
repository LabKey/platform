/* exp-16.30-16.31.sql */

ALTER TABLE exp.MaterialSource ADD COLUMN NameExpression VARCHAR(200) NULL;

/* exp-16.31-16.32.sql */

SELECT core.executeJavaUpgradeCode('cleanupQuotedAliases');

/* exp-16.32-16.33.sql */

ALTER TABLE exp.material
    ADD description VARCHAR(4000);

/* exp-16.33-16.34.sql */

SELECT core.fn_dropifexists('indexinteger', 'exp', 'TABLE', NULL);
SELECT core.fn_dropifexists('indexvarchar', 'exp', 'TABLE', NULL);
SELECT core.fn_dropifexists('list', 'exp', 'CONSTRAINT', 'UQ_RowId');
SELECT core.fn_dropifexists('list', 'exp', 'COLUMN', 'rowid');