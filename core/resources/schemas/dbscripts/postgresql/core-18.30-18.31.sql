
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
