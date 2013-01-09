ALTER TABLE core.viewcategory
    ADD COLUMN Parent int4,
    ADD CONSTRAINT fk_viewcategory_parent FOREIGN KEY (rowid) REFERENCES core.viewcategory(rowid) ON DELETE CASCADE;