-- remove limit on stringvalue field
ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue NVARCHAR(MAX) NULL;
