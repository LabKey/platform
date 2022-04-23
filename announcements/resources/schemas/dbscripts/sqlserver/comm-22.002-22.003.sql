-- Switch from PK to UNIQUE INDEX to match PostgreSQL
ALTER TABLE comm.PageAliases DROP CONSTRAINT PK_PageAliases;
CREATE UNIQUE INDEX UQ_PageAliases ON comm.PageAliases (Container, Alias);