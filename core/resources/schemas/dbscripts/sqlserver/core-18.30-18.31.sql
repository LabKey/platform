
EXEC core.fn_dropifexists 'portalwebparts', 'core', 'CONSTRAINT', 'FK_PortalWebPartPages';
EXEC core.fn_dropifexists 'portalpages', 'core', 'CONSTRAINT', 'PK_PortalPages';
EXEC core.fn_dropifexists 'portalpages', 'core', 'CONSTRAINT', 'UQ_PortalPage';

ALTER TABLE core.PortalPages ADD RowId INT IDENTITY(1,1) NOT NULL;
ALTER TABLE core.PortalWebParts ADD PortalPageId INT;
GO

ALTER TABLE core.PortalPages ADD CONSTRAINT PK_PortalPages PRIMARY KEY (RowId);
UPDATE core.portalwebparts SET PortalPageId = page.RowId
    FROM core.PortalPages page, core.PortalWebParts web
    WHERE web.PageId = page.PageId;
GO

ALTER TABLE core.PortalWebParts DROP COLUMN PageId;
ALTER TABLE core.PortalWebParts ALTER COLUMN PortalPageId INT NOT NULL;
ALTER TABLE core.PortalWebParts ADD CONSTRAINT FK_PortalWebPartPages FOREIGN KEY (PortalPageId) REFERENCES core.PortalPages (rowId);
GO
