
ALTER TABLE core.Notifications ADD Description NVARCHAR(MAX);
ALTER TABLE core.Notifications ADD ReadOn DATETIME;
ALTER TABLE core.Notifications ADD ActionLinkText NVARCHAR(2000);
ALTER TABLE core.Notifications ADD ActionLinkURL NVARCHAR(4000);
ALTER TABLE core.Notifications DROP COLUMN ModifiedBy;
ALTER TABLE core.Notifications DROP COLUMN Modified;