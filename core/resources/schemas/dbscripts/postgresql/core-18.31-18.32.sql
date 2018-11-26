
-- Fix webparts that referenced incorrect portal pages
UPDATE core.portalwebparts parts
    SET portalPageId = (SELECT rowId AS newPortalPageId FROM core.portalpages p2
                        WHERE p2.pageid = page.pageid AND p2.Container = parts.Container)
FROM core.portalpages page
WHERE page.rowid = parts.portalpageid AND page.Container <> parts.Container;
