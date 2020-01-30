-- Change SortOrder type to SMALLINT and default to MAX_SMALL_INT to ensure that new configurations appear at the bottom of the list by default
EXEC core.fn_dropifexists 'AuthenticationConfigurations', 'core', 'DEFAULT', 'SortOrder';
ALTER TABLE core.AuthenticationConfigurations ALTER COLUMN SortOrder SMALLINT;
ALTER TABLE core.AuthenticationConfigurations ADD CONSTRAINT DF_SortOrder DEFAULT 32767 FOR SortOrder;
