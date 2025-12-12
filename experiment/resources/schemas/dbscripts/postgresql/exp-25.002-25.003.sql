--GitHub Issue #652: materialinput Role can be a field name, which has a max length of 200 characters
ALTER TABLE exp.MaterialInput ALTER COLUMN Role TYPE VARCHAR(200);