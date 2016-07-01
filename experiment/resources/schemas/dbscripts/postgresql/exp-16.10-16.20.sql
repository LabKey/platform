/* exp-16.10-16.11.sql */

ALTER TABLE exp.data
   ALTER COLUMN cpastype TYPE varchar(300);

UPDATE exp.data d
  SET cpastype = (SELECT lsid FROM exp.dataclass WHERE d.classId = exp.dataclass.rowid)
  WHERE classid IS NOT NULL;

/* exp-16.11-16.12.sql */

ALTER TABLE exp.Data ADD COLUMN LastIndexed TIMESTAMP NULL;

/* exp-16.12-16.13.sql */

ALTER TABLE exp.DomainDescriptor ADD COLUMN TemplateInfo VARCHAR(4000) NULL;