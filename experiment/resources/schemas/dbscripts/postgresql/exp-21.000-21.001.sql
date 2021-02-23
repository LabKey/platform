-- Most major file systems cap file lengths at 255 characters. Let's do the same
ALTER TABLE exp.data ALTER COLUMN Name TYPE VARCHAR(255);