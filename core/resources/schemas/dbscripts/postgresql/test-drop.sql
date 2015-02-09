
-- DROP all views (current and obsolete)

-- NOTE: Don't remove any of these drop statements, even if we stop re-creating the view in *-create.sql. Drop statements must
-- remain in place so we can correctly upgrade from older versions, which we commit to for two years after each release.

SELECT core.fn_dropifexists('TestTable3', 'test', 'VIEW', NULL);
SELECT core.fn_dropifexists('Containers2', 'test', 'VIEW', NULL);
SELECT core.fn_dropifexists('ContainerAliases2', 'test', 'VIEW', NULL);
SELECT core.fn_dropifexists('Users2', 'test', 'VIEW', NULL);
