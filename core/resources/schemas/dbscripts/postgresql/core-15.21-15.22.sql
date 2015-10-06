
ALTER TABLE core.Notifications ADD Description TEXT;
ALTER TABLE core.Notifications ADD ReadOn TIMESTAMP;
ALTER TABLE core.Notifications ADD ActionLinkText VARCHAR(2000);
ALTER TABLE core.Notifications ADD ActionLinkURL VARCHAR(4000);
ALTER TABLE core.Notifications DROP COLUMN ModifiedBy;
ALTER TABLE core.Notifications DROP COLUMN Modified;