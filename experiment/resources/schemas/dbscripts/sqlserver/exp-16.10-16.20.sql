/* exp-16.10-16.11.sql */

ALTER TABLE exp.data
   ALTER COLUMN cpastype nvarchar(300);

UPDATE exp.data
  SET cpastype = (SELECT lsid FROM exp.dataclass WHERE exp.data.classId = exp.dataclass.rowid)
  WHERE classid IS NOT NULL;

/* exp-16.11-16.12.sql */

ALTER TABLE exp.Data ADD LastIndexed DATETIME NULL;

/* exp-16.12-16.13.sql */

ALTER TABLE exp.DomainDescriptor ADD TemplateInfo NVARCHAR(4000) NULL;