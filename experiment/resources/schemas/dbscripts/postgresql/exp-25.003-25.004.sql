--Issue 53478: DataInput Role can be a DataClass name, which has a max length of 200 characters
ALTER TABLE exp.DataInput ALTER COLUMN Role TYPE VARCHAR(200);