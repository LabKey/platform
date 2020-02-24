
ALTER TABLE assay.plate ADD
    Modified DATETIME,
    ModifiedBy USERID;
GO

UPDATE assay.plate SET Modified = Created,
                       ModifiedBy = CreatedBy;
GO

ALTER TABLE assay.plate ALTER COLUMN Modified DATETIME NOT NULL;
ALTER TABLE assay.plate ALTER COLUMN ModifiedBy USERID NOT NULL;

ALTER TABLE assay.plate
    ADD CONSTRAINT uq_plate_lsid UNIQUE (lsid);

ALTER TABLE assay.well
    ADD CONSTRAINT uq_well_lsid UNIQUE (lsid);

ALTER TABLE assay.wellgroup
    ADD CONSTRAINT uq_wellgroup_lsid UNIQUE (lsid);

-- plate names are unique in each container
ALTER TABLE assay.plate
    ADD CONSTRAINT uq_plate_container_name UNIQUE (container, name);

-- each well position is unique on the plate
ALTER TABLE assay.well
    ADD CONSTRAINT uq_well_plateid_row_col UNIQUE (plateid, row, col);

-- well group names must be unique within each well group type
ALTER TABLE assay.wellgroup
    ADD CONSTRAINT uq_wellgroup_plateid_typename_name UNIQUE (plateid, typename, name);
