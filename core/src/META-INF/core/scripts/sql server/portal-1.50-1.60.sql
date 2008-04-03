ALTER TABLE portal.portalwebparts ADD Permanent BIT NOT NULL DEFAULT 0
GO

DELETE FROM portal.PortalWebParts
	WHERE NOT EXISTS (SELECT EntityId from core.Containers C WHERE C.EntityId = PageId)
GO
