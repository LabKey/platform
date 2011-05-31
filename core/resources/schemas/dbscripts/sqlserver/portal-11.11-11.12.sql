-- Table: portal.portalwebparts

-- DROP TABLE portal.portalwebparts;

ALTER TABLE Portal.PortalWebParts
    ADD Container ENTITYID NULL
GO

UPDATE Portal.PortalWebParts SET Container=PageId
GO

ALTER TABLE Portal.PortalWebParts
   ALTER COLUMN Container ENTITYID NOT NULL
GO
