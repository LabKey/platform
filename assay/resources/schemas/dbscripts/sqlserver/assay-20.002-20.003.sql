-- Issue 39805: uq_plate_container_name violation when upgrading via assay-20.001-20.002.sql
EXEC core.fn_dropifexists 'plate', 'assay', 'CONSTRAINT', 'uq_plate_container_name';

-- plate template (not instances) names are unique in each container
CREATE UNIQUE INDEX uq_plate_container_name_template ON assay.plate (container, name) WHERE template=1;