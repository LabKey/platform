--Issue 53478: DataInput Role can be a DataClass name, which has a max length of 200 characters
EXECUTE core.fn_dropifexists 'DataInput', 'exp', 'INDEX', 'IDX_DataInput_Role';
ALTER TABLE exp.DataInput ALTER COLUMN Role NVARCHAR(200) NOT NULL;
CREATE INDEX IDX_DataInput_Role ON exp.DataInput(Role);