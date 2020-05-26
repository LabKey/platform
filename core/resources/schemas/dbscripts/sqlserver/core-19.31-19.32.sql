ALTER TABLE core.EmailPrefs DROP CONSTRAINT FK_EmailPrefs_PageTypes;
ALTER TABLE core.EmailPrefs DROP COLUMN PageTypeId;
DROP TABLE core.PageTypes;

ALTER TABLE core.EmailPrefs DROP CONSTRAINT FK_EmailPrefs_EmailFormats;
ALTER TABLE core.EmailPrefs DROP COLUMN EmailFormatId;
DROP TABLE core.EmailFormats;