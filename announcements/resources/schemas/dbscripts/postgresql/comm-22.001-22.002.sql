ALTER TABLE comm.PageAliases RENAME COLUMN RowId TO PageRowId;

-- Aliases should be case-insensitive
ALTER TABLE comm.PageAliases DROP CONSTRAINT PK_PageAliases;
CREATE UNIQUE INDEX UQ_PageAliases ON comm.PageAliases (Container, LOWER(Alias));
