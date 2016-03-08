
ALTER TABLE exp.data
   ALTER COLUMN cpastype nvarchar(300);

UPDATE exp.data
  SET cpastype = (SELECT lsid FROM exp.dataclass WHERE exp.data.classId = exp.dataclass.rowid)
  WHERE classid IS NOT NULL;
