DELETE FROM portal.PortalWebParts
	WHERE NOT EXISTS (SELECT EntityId from core.Containers C WHERE C.EntityId = PageId);
