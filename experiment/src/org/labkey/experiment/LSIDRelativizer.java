package org.labkey.experiment;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.labkey.api.exp.Lsid;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * User: jeckels
 * Date: Nov 21, 2005
 */
public enum LSIDRelativizer
{
    ABSOLUTE("Absolute")
    {
        public String relativize(String lsid, RelativizedLSIDs lsids)
        {
            return lsid;
        }
    },
    FOLDER_RELATIVE("Folder relative")
    {
        public String relativize(String s, RelativizedLSIDs lsids)
        {
            if (s == null)
            {
                return null;
            }

            String result = lsids.getExistingLSID(s);
            if (result != null)
            {
                return result;
            }

            Lsid lsid = new Lsid(s);
            String prefix = lsid.getNamespacePrefix();
            String suffix = lsid.getNamespaceSuffix();
            if (prefix == null)
            {
                result = s;
            }
            else if (prefix.equals("ExperimentRun"))
            {
                result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:ExperimentRun.Folder-${Container.RowId}.${XarFileId}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if (prefix.equals("ProtocolApplication"))
            {
                result = lsids.uniquifyRelativizedLSID("${RunLSIDBase}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if (prefix.equals("Sample"))
            {
                result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:Sample.Folder-${Container.RowId}.${XarFileId}-" + lsids.getNextSampleId(), lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if (prefix.equals("Material"))
            {
                result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:Material.Folder-${Container.RowId}.${XarFileId}-" + lsids.getNextMaterialId(), lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if (prefix.equals("Data"))
            {
                result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:Data.Folder-${Container.RowId}.${XarFileId}-" + lsids.getNextDataId(), lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if (suffix != null && SUFFIX_PATTERN.matcher(suffix).matches())
            {
                result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:" + prefix + ".Folder-${Container.RowId}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else
            {
                result = s;
            }
            lsids.putLSID(s, result);
            return result;
        }
    },
    PARTIAL_FOLDER_RELATIVE("Partial folder relative")
    {
        public String relativize(String s, RelativizedLSIDs lsids)
        {
            if (s == null)
            {
                return null;
            }

            String result = lsids.getExistingLSID(s);
            if (result != null)
            {
                return result;
            }

            Lsid lsid = new Lsid(s);
            String prefix = lsid.getNamespacePrefix();
            String suffix = lsid.getNamespaceSuffix();
            if (prefix == null)
            {
                result = s;
            }
            else if (prefix.equals("ExperimentRun"))
            {
                result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:ExperimentRun.Folder-${Container.RowId}.${XarFileId}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if (prefix.equals("ProtocolApplication"))
            {
                result = lsids.uniquifyRelativizedLSID("${RunLSIDBase}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if (prefix.equals("Sample"))
            {
                result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:Sample.Folder-${Container.RowId}.${XarFileId}-" + lsids.getNextSampleId(), lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if (prefix.equals("Material"))
            {
                if (lsid.getNamespaceSuffix().startsWith("Folder-"))
                {
                    result = stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:Material.Folder-${Container.RowId}.${XarFileId}-" + lsids.getNextMaterialId(), lsid.getObjectId(), lsid.getVersion(), null);
                }
            }
            else if (prefix.equals("Data"))
            {
                if (lsid.getNamespaceSuffix().startsWith("Folder-"))
                {
                    result = stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:Data.Folder-${Container.RowId}.${XarFileId}-" + lsids.getNextDataId(), lsid.getObjectId(), lsid.getVersion(), null);
                }
            }
            else
            {
                if (suffix != null && suffix.startsWith("Folder-"))
                {
                    result = stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    result = lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:" + prefix + ".Folder-${Container.RowId}", lsid.getObjectId(), lsid.getVersion(), null);
                }
            }
            lsids.putLSID(s, result);
            return result;
        }
    };
    private final String _description;

    public abstract String relativize(String lsid, RelativizedLSIDs lsids);

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("Folder-[0-9]+"); 

    private LSIDRelativizer(String description)
    {
        _description = description;
    }

    private static String stripFolderSuffix(Lsid lsid, RelativizedLSIDs lsids)
    {
        String suffix = lsid.getNamespaceSuffix();
        int index = "Folder-".length();
        while (index < suffix.length() && Character.isDigit(suffix.charAt(index)))
        {
            index++;
        }
        return lsids.uniquifyRelativizedLSID("urn:lsid:${LSIDAuthority}:" + lsid.getNamespacePrefix() + ".Folder-${Container.RowId}" + suffix.substring(index), lsid.getObjectId(), lsid.getVersion(), null);

    }

    public String getDescription()
    {
        return _description;
    }

    public static class RelativizedLSIDs
    {
        private Map<String, String> _lsids = new HashMap<String, String>();

        private int _nextDataId = 1;
        private int _nextSampleId = 1;
        private int _nextMaterialId = 1;

        public RelativizedLSIDs() {}

        private String getExistingLSID(String originalLSID)
        {
            return _lsids.get(originalLSID);
        }

        private void putLSID(String originalLSID, String relativizedLSID)
        {
            assert !_lsids.containsKey(originalLSID);
            _lsids.put(originalLSID, relativizedLSID);
        }

        private String uniquifyRelativizedLSID(String prefix, String objectId, String version, Integer exportVersion)
        {
            StringBuilder sb = new StringBuilder(prefix);
            sb.append(":");
            sb.append(objectId);
            if (version != null && !(version.length() == 0))
            {
                sb.append(":");
                sb.append(version);

                if (exportVersion != null)
                {
                    sb.append("-Export");
                    sb.append(exportVersion.toString());
                }
            }
            else if (exportVersion != null)
            {
                sb.append(":Export");
                sb.append(exportVersion.toString());
            }
            String newLSID = sb.toString();

            boolean foundMatch = false;
            for (String existing : _lsids.values())
            {
                if (newLSID.equals(existing))
                {
                    foundMatch = true;
                    break;
                }
            }

            if (foundMatch)
            {
                Integer newExportVersion;
                if (exportVersion == null)
                {
                    newExportVersion = new Integer( 1 );
                }
                else
                {
                    newExportVersion = new Integer(exportVersion.intValue() + 1);
                }

                return uniquifyRelativizedLSID(prefix, objectId, version, newExportVersion);
            }
            else
            {
                return newLSID;
            }
        }

        public int getNextDataId()
        {
            return _nextDataId++;
        }

        public int getNextSampleId()
        {
            return _nextSampleId++;
        }

        public int getNextMaterialId()
        {
            return _nextMaterialId++;
        }
    }

    public static class TestCase extends junit.framework.TestCase
    {
        public void testOverlappingLSIDs()
        {
            RelativizedLSIDs set = new RelativizedLSIDs();
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-1:MS2.PreSearch", set));
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:Export1", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-2:MS2.PreSearch", set));
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:Export2", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-3:MS2.PreSearch", set));

            // Make sure they resolve to the same thing as they did the first time
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-1:MS2.PreSearch", set));
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:Export1", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-2:MS2.PreSearch", set));
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:Export2", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-3:MS2.PreSearch", set));

            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:v1", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-1:MS2.PreSearch:v1", set));
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:v1-Export1", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-2:MS2.PreSearch:v1", set));
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:v1-Export2", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-3:MS2.PreSearch:v1", set));

            // Make sure they resolve to the same thing as they did the first time
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:v1", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-1:MS2.PreSearch:v1", set));
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:v1-Export1", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-2:MS2.PreSearch:v1", set));
            assertEquals("urn:lsid:${LSIDAuthority}:Protocol.Folder-${Container.RowId}:MS2.PreSearch:v1-Export2", FOLDER_RELATIVE.relativize("urn:lsid:localhost:Protocol.Folder-3:MS2.PreSearch:v1", set));
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
