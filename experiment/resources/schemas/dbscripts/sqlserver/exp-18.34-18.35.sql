
UPDATE exp.data SET datafileurl = 'file:///' + substring(datafileurl, 7, 600) WHERE datafileurl LIKE 'file:/_%' AND datafileurl NOT LIKE 'file:///%' AND datafileurl IS NOT NULL;