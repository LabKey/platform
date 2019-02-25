
UPDATE exp.data SET datafileurl = 'file:///' || substr(datafileurl, 7) WHERE datafileurl LIKE 'file:/_%' AND datafileurl NOT LIKE 'file:///%' AND datafileurl IS NOT NULL;