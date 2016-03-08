
ALTER TABLE exp.data
   ALTER COLUMN cpastype TYPE varchar(300);

UPDATE exp.data d
  SET cpastype = (SELECT lsid FROM exp.dataclass WHERE d.classId = exp.dataclass.rowid)
  WHERE classid IS NOT NULL;
