
-- Clean up duplicated portal page definitions from multi-tabbed folder types. We've been creating 'portal.default'
-- pages for each new container, but then immediately migrating it to 'DefaultDashboard' and leaving the original in
-- place

-- First the web parts
DELETE FROM core.PortalWebparts
WHERE PortalPageId IN (SELECT pp1.RowId
                       FROM core.PortalPages pp1
                                INNER JOIN
                            core.PortalPages pp2 ON pp1.Container = pp2.Container AND
                                                    pp1.PageId = 'portal.default' AND
                                                    pp2.PageId = 'DefaultDashboard'
                                INNER JOIN prop.PropertySets ps
                                           on ps.ObjectId = pp1.Container and ps.Category = 'folderType'
                                INNER JOIN prop.Properties p
                                           on ps."set" = p."set" and p.Name = 'name' and p.Value != 'None')
-- Menu bars are always stored in 'portal.default' regardless of the rest of the portal layout
AND Location != 'menubar';

-- And then the page itself
DELETE FROM core.PortalPages
WHERE RowId IN (SELECT pp1.RowId
                FROM core.PortalPages pp1
                         INNER JOIN
                     core.PortalPages pp2 ON pp1.Container = pp2.Container AND
                                             pp1.PageId = 'portal.default' AND
                                             pp2.PageId = 'DefaultDashboard'
                         INNER JOIN prop.PropertySets ps
                                    on ps.ObjectId = pp1.Container and ps.Category = 'folderType'
                         INNER JOIN prop.Properties p
                                    on ps."set" = p."set" and p.Name = 'name' and p.Value != 'None')
AND RowId NOT IN (SELECT pwp.PortalPageId FROM core.PortalWebparts pwp);
