-- Table: portal.portalwebparts

-- DROP TABLE portal.portalwebparts;

ALTER TABLE portal.portalwebparts ADD COLUMN container entityid;
UPDATE portal.portalwebparts SET container=pageid;
ALTER TABLE portal.portalwebparts
   ALTER COLUMN container SET NOT NULL;