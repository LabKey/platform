ALTER TABLE core.ViewCategory
	ADD Parent INT;

ALTER TABLE core.ViewCategory
	ADD CONSTRAINT FK_ViewCategory_Parent FOREIGN KEY (RowId) REFERENCES core.ViewCategory(RowId);