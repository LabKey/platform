ALTER TABLE portal.portalwebparts ADD Permanent Boolean NOT NULL DEFAULT FALSE;

DELETE FROM portal.PortalWebParts
	WHERE NOT EXISTS (SELECT EntityId from core.Containers C WHERE C.EntityId = PageId);
