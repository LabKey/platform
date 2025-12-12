--GitHub Issue #652: materialinput Role can be a field name, which has a max length of 200 characters
EXECUTE core.fn_dropifexists 'MaterialInput', 'exp', 'INDEX', 'IDX_MaterialInput_Role';
ALTER TABLE exp.MaterialInput ALTER COLUMN Role NVARCHAR(200) NOT NULL;
CREATE INDEX IDX_MaterialInput_Role ON exp.MaterialInput(Role);