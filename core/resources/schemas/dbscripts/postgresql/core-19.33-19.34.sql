-- Change SortOrder type to SMALLINT and default to MAX_SMALL_INT to ensure that new configurations appear at the bottom of the list by default
SELECT core.fn_dropifexists('AuthenticationConfigurations', 'core', 'DEFAULT', 'SortOrder');
ALTER TABLE core.AuthenticationConfigurations ALTER COLUMN SortOrder TYPE SMALLINT;
ALTER TABLE core.AuthenticationConfigurations ALTER COLUMN SortOrder SET DEFAULT 32767;
