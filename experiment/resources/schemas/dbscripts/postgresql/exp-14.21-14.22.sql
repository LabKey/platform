
-- Add batchId column to run table
ALTER TABLE exp.ExperimentRun
   ADD BatchId INT;

ALTER TABLE exp.ExperimentRun
  ADD CONSTRAINT fk_ExperimentRun_BatchId FOREIGN KEY (BatchId) REFERENCES exp.Experiment (RowId);

CREATE INDEX IX_ExperimentRun_BatchId
  ON exp.ExperimentRun(BatchId);


UPDATE exp.ExperimentRun r SET BatchId = (
  SELECT e.RowId AS BatchId
    FROM exp.Experiment e
    WHERE
      e.BatchProtocolId IS NOT NULL
      AND e.RowId = (
        SELECT MIN(ExperimentId) FROM exp.Experiment e, exp.RunList rl
        WHERE e.RowId = rl.ExperimentId
        AND rl.ExperimentRunid = r.RowId
      )
);
