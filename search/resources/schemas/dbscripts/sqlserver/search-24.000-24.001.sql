-- Previously, every search schema bump resulted in aggressive reindexing; now we call the reindex() upgrade method
-- explicitly. This upgrade script accommodates a major Lucene upgrade corresponding to an old 24.001 schema bump,
-- ensuring that old servers are reindexed on upgrade. Issue #52513
EXEC core.executeJavaUpgradeCode 'reindex';
