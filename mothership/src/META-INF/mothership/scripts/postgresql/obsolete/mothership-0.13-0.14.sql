SET search_path TO mothership, public;  -- include public to get ENTITYID, USERID

ALTER TABLE ServerInstallation ADD COLUMN ServerHostName VARCHAR(256);
