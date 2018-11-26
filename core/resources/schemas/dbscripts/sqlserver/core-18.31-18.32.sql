
-- Fix webparts that referenced incorrect portal pages
UPDATE core.portalwebparts
    SET portalPageId = (SELECT rowId AS newPortalPageId FROM core.portalpages p2
                        WHERE p2.pageid = page.pageid AND p2.Container = parts.Container)
FROM core.portalwebparts parts
JOIN core.portalpages page ON page.rowid = parts.portalpageid
WHERE page.Container <> parts.Container