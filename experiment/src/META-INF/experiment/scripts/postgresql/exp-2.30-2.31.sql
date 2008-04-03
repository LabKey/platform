select core.fn_dropifexists('experimentrun', 'cabig','VIEW', NULL);

ALTER TABLE exp.ExperimentRun ALTER COLUMN Name TYPE VARCHAR(100);
