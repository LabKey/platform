-- Add missing indices on comm.Pages
CREATE INDEX IDX_Pages_PageVersionId ON comm.Pages(PageVersionId);
CREATE INDEX IDX_Pages_Parent ON comm.Pages(Parent);
CREATE UNIQUE INDEX UQ_Pages_RowId ON comm.Pages(RowId);

-- Switch from -1 to NULL for no parent
ALTER TABLE comm.Pages ALTER COLUMN Parent INT NULL;
UPDATE comm.Pages SET Parent = NULL WHERE Parent = -1;

-- Clean up any pages that point at a parent that doesn't exist anymore
UPDATE comm.Pages SET Parent = NULL WHERE Parent NOT IN (SELECT RowId FROM comm.Pages);

-- Add a FK
ALTER TABLE comm.Pages
    ADD CONSTRAINT FK_Pages_Parent FOREIGN KEY (Parent)
        REFERENCES comm.Pages (RowId);
