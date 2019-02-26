SELECT core.fn_dropifexists('portalwebparts', 'core', 'CONSTRAINT', 'fk_portalwebpartpages');
SELECT core.fn_dropifexists('portalpages', 'core', 'CONSTRAINT', 'pk_portalpages');
SELECT core.fn_dropifexists('portalpages', 'core', 'CONSTRAINT', 'uq_portalpage');
ALTER TABLE core.portalpages ADD COLUMN rowId SERIAL PRIMARY KEY;
ALTER TABLE core.portalwebparts ADD COLUMN portalPageId INTEGER;
UPDATE core.portalwebparts web SET portalPageId = page.rowId
    FROM core.portalpages page
    WHERE web.pageId = page.pageId;
ALTER TABLE core.portalwebparts DROP COLUMN pageId;
ALTER TABLE core.portalwebparts ALTER COLUMN portalPageId SET NOT NULL;
ALTER TABLE core.portalwebparts ADD CONSTRAINT fk_portalwebpartpages FOREIGN KEY (portalPageId) REFERENCES core.portalpages (rowId);

-- Fix webparts that referenced incorrect portal pages
UPDATE core.portalwebparts parts
    SET portalPageId = (SELECT rowId AS newPortalPageId FROM core.portalpages p2
                        WHERE p2.pageid = page.pageid AND p2.Container = parts.Container)
FROM core.portalpages page
WHERE page.rowid = parts.portalpageid AND page.Container <> parts.Container;

UPDATE core.RoleAssignments SET role = 'org.labkey.api.security.roles.SeeUserDetailsRole' WHERE role = 'org.labkey.api.security.roles.SeeEmailAddressesRole';

UPDATE core.RoleAssignments SET role = 'org.labkey.api.security.roles.QCAnalystRole' WHERE role = 'org.labkey.api.security.roles.QCEditorRole';

ALTER TABLE core.ReportEngineMap DROP CONSTRAINT PK_ReportEngineMap;
ALTER TABLE core.ReportEngineMap ADD EngineContext VARCHAR(64) NOT NULL DEFAULT 'report';
ALTER TABLE core.ReportEngineMap ADD CONSTRAINT PK_ReportEngineMap PRIMARY KEY (EngineId, Container, EngineContext);

SELECT core.executeJavaUpgradeCode('migrateSiteDefaultEngines');