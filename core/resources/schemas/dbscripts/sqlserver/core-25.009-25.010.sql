-- In 2019, we added support for multiple authentication configurations per provider and, at that time, attached
-- authentication logos to each configuration. This deletes any old, orphaned, one-per-provider authentication logos
-- that were attached directly to the root container.
DELETE FROM core.Documents WHERE
    Container = (SELECT EntityId FROM core.Containers WHERE Parent IS NULL) AND
    Parent = (SELECT EntityId FROM core.Containers WHERE Parent IS NULL) AND
    ParentType IS NULL AND
    (DocumentName LIKE 'auth_header_logo_%' OR DocumentName LIKE 'auth_login_page_logo_%');
