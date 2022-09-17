EXEC core.fn_dropifexists 'Study', 'study', 'DEFAULT', 'AllowReload';
ALTER TABLE study.study DROP COLUMN AllowReload;
ALTER TABLE study.study DROP COLUMN LastReload;
ALTER TABLE study.study DROP COLUMN ReloadInterval;
ALTER TABLE study.study DROP COLUMN ReloadUser;
