/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.query.olap;

import org.json.JSONObject;
import org.olap4j.Cell;
import org.olap4j.CellSet;
import org.olap4j.CellSetAxis;
import org.olap4j.CellSetAxisMetaData;
import org.olap4j.CellSetMetaData;
import org.olap4j.OlapException;
import org.olap4j.Position;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.MetadataElement;
import org.olap4j.metadata.NamedList;
import org.olap4j.metadata.Property;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.TreeMap;

/**
 * User: matthewb
 * Date: 4/15/12
 * Time: 10:00 AM
 *
 * Translate Olap4j objects to json
 */
public class Olap4Js
{
    private static class CellSetWriter
    {
        boolean _includeLevelMembers = false;
        boolean _includeMemberProperties = true;
        int _indent = 0;
        static String spaces = "                                                 ";
        TreeMap<String, String> _idMap = new TreeMap<>();

        String idFor(Class c, MetadataElement el, boolean create)
        {
            return idFor(c.getSimpleName(), el.getUniqueName(), create);
        }

        String idFor(String type, String name, boolean create)
        {
            String key = type + ":" + name;
            String value = _idMap.get(key);
            if (null != value || !create)
                return value;
            value = type.substring(0,1) + (_idMap.size()+1);
            _idMap.put(key, value);
            return value;
        }

        void indent(String pre, Writer out) throws IOException
        {
            out.write(pre);
            out.write("\n");
            out.write(spaces, 0, Math.min(spaces.length(), _indent*2));
        }

        void indent(Writer out, String post) throws IOException
        {
            out.write("\n");
            out.write(spaces, 0, Math.min(spaces.length(), _indent*2));
            out.write(post);
        }

        void indent(Writer out) throws IOException
        {
            out.write("\n");
            out.write(spaces, 0, Math.min(spaces.length(), _indent*2));
        }

        void write(CellSet cs, Writer out) throws IOException, OlapException
        {
            write(cs, true, out);
        }

        void write(CellSet cs, boolean withMetaData, Writer out) throws IOException, OlapException
        {
            _includeMemberProperties = false;

            CellSetMetaData csmd = cs.getMetaData();
            NamedList<Property> cellProperties = csmd.getCellProperties();

            String comma = "";
            int cellCount = 1;
            out.write("{");
            _indent++;

            //
            // AXES
            //

            indent(out, "\"axes\":");
            indent(out, "[");
            _indent++;
            for (CellSetAxis axis : cs.getAxes())
            {
                indent(comma, out); comma = ",";
                write(axis, out);
                cellCount *= axis.getPositionCount();
            }
            _indent--;
            indent(out);
            out.write("],");

            // CELLS

            indent(out, "\"cells\":");

            if (cs.getAxes().size()!=2)   // FLAT LIST
            {
                indent(out, "[");
                _indent++;
                comma = "";
                for (int c=0 ; c<cellCount ; c++)
                {
                    indent(comma, out); comma = ",";
                    write(cs.getCell(c), cellProperties, out);
                }
                _indent--;
                indent(out, "]");
            }
            else    // 2-D LIST
            {
                indent(out, "[");
                _indent++;

                int rowCount = cs.getAxes().size() > 1 ? cs.getAxes().get(1).getPositionCount() : 1;
                int colCount = cs.getAxes().get(0).getPositionCount();
                int c = 0;
                for (int row=0 ; row<rowCount ; row++)
                {
                    if (row > 0) out.write(",");
                    indent(out, "[");
                    _indent++;
                    comma = "";
                    for (int col=0 ; col<colCount ; col++, c++)
                    {
                        //indent(comma, out); comma = ",";
                        out.write(comma); comma=",";
                        // TODO assert expected cell position
                        write(cs.getCell(c), cellProperties, out);
                    }
                    _indent--;
                    //indent(out, "]");
                    out.write("]");
                }

                _indent--;
                indent(out, "]");
            }

            if (withMetaData)
            {
                indent(",", out);
                out.write("\"metadata\":");
                write(csmd, out);
            }

            _indent--;
            indent(out, "}");
        }


        void write(CellSetMetaData md, Writer out) throws IOException
        {
            indent(out, "{");
            _indent++;
            indent(out, "\"cube\":");
            write(md.getCube(), false, out);
            indent(",",out);
            out.write("\"axesMetaData\":");
            indent(out, "[");
            _indent++;
            String comma = "";
            for (CellSetAxisMetaData amd : md.getAxesMetaData())
            {
                indent(comma, out); comma = ",";
                write(amd, out);
            }
            _indent--;
            indent(out, "]");
            _indent--;
            indent(out, "}");
        }


        void write(Cube cube, boolean showAll, Writer out) throws IOException
        {
            indent(out);
            out.write("{");
            _indent++;
            indent(out);
            out.write("\"name\":" + valueToString(cube.getName()) + ",");
            indent(out);
            out.write("\"description\":" + valueToString(cube.getDescription()) + ",");
            indent(out);
            out.write("\"dimensions\":");
            indent(out);
            out.write("[");
            _indent++;
            String comma = "";
            for (Dimension d : cube.getDimensions())
            {
                if (!showAll && null == idFor(Dimension.class, d, false))
                    continue;
                indent(comma, out); comma = ",";
                write(d, showAll, out);
            }
            _indent--;
            indent(out,"]");
            _indent--;
            indent(out, "}");
        }


        void write(CellSetAxisMetaData amd, Writer out) throws IOException
        {
            out.write("{");
            _indent++;
            indent(out);
            out.write("\"axisOrdinal\":" + valueToString(amd.getAxisOrdinal()));
            indent(",", out);
            String comma="";
//            out.write("\"properties\"":[");
//            for (Property p : amd.getProperties())
//            {
//                out.write(comma); comma=","; out.write("{");
//                out.write("\"name\":"+valueToString(p.getName())+
//                        ",uniqueName:"+valueToString(p.getUniqueName())+
//                        ",description:"+valueToString(p.getDescription())+
//                        ",caption:"+valueToString(p.getCaption()));
//                out.write("}");
//            }
//            out.write("],");
            indent(out);
            out.write("\"hierarchies\":[");
            comma = "";
            for (Hierarchy h : amd.getHierarchies())
            {
                if (null == h) continue;
                out.write(comma); comma = ",";
                writeRef(h, out);
            }
            out.write("]");
            _indent--;
            indent(out, "}");
        }


        // write out dimension as reference
        void writeRef(Dimension d, Writer out) throws IOException
        {
            String id = idFor(Dimension.class, d, true);
            out.write(valueToString("#" + id));
        }


        void write(Dimension d, boolean showAll, Writer out) throws IOException
        {
            indent(out);
            out.write("{");
            _indent++;
            indent(out);
            out.write("\"id\":" + valueToString(idFor(Dimension.class, d, true)) + ",");
            out.write("\"name\":" + valueToString(d.getName()) + ",");
            out.write("\"uniqueName\":" + valueToString(d.getUniqueName()) + ",");
            out.write("\"hierarchies\":");
            indent(out,"[");
            _indent++;
            String comma = "";
            for (Hierarchy h : d.getHierarchies())
            {
                if (!showAll && null == idFor(Hierarchy.class, h, false))
                    continue;
                indent(comma, out); comma = ",";
                write(h, out);
            }
            _indent--;
            indent(out, "]");
            _indent--;
            indent(out, "}");
        }


        // write out hierarchy as reference
        void writeRef(Hierarchy h, Writer out) throws IOException
        {
            String id = idFor(Hierarchy.class, h, true);
            out.write(valueToString("#" + id));
        }


        void write(Hierarchy h, Writer out) throws IOException
        {
            indent(out);
            out.write("{");
            _indent++;
            indent(out);
            out.write("\"id\":" + valueToString(idFor(Hierarchy.class, h, true)) + ",");
            out.write("\"name\":" + valueToString(h.getName()) + ",");
            out.write("\"uniqueName\":" + valueToString(h.getUniqueName()) + ",");
            out.write("\"levels\":");
            indent(out,"[");
            _indent++;
            String comma = "";
            for (Level l : h.getLevels())
            {
                indent(comma, out); comma = ",";
                write(l, true, out);
            }
            _indent--;
            indent(out, "]");
            _indent--;
            indent(out, "}");
        }


        // write out level as a reference
        void writeRef(Level l, Writer out) throws IOException
        {
            // make sure we have a ref for the dimension/hierarchy
            idFor(Dimension.class, l.getHierarchy().getDimension(), true);
            idFor(Hierarchy.class, l.getHierarchy(), true);

            String id = idFor(Level.class, l, true);
            out.write(valueToString("#" + id));
        }


        void write(Level l, Writer out) throws IOException
        {
            write(l, _includeLevelMembers, out);
        }


        void write(Level l, boolean withMembers, Writer out) throws IOException
        {
            out.write("{");
            out.write("\"id\":" + valueToString(idFor(Level.class, l, true)) + ",");
            out.write("\"name\":" + valueToString(l.getName()) + ",");
            out.write("\"uniqueName\":" + valueToString(l.getUniqueName()) + ",");
            out.write("\"depth\":" + l.getDepth());

            if (_includeMemberProperties)
            {
                indent(",", out);
                NamedList<Property> properties = l.getProperties();
                String comma = "";
                indent("\"propertyNames\":[",out);
                for (Property p : properties)
                {
                    out.write(comma);
                    out.write(valueToString(p.getName()));
                    comma = ",";
                }
                out.write("]");
            }

            if (withMembers)
            {
                List<Member> members = null;
                try
                {
                    members = l.getMembers();
                    indent(",", out);
                    indent("\"members\":", out);
                    indent("[", out);
                    _indent++;
                    String comma = "";
                    for (Member m : members)
                    {
                        indent(comma, out); comma = ",";
                        write(m, false, out);
                    }
                    _indent--;
                    indent("]", out);
                }
                catch (OlapException x)
                {
                    ;
                }
            }
            out.write("}");
        }


        void write(CellSetAxis axis, Writer out) throws IOException, OlapException
        {
            out.write("{");
            _indent++;
            indent(out);
            out.write("\"axisOrdinal\":" + valueToString(axis.getAxisOrdinal()) + ",");
            indent(out);
            out.write("\"positions\":");
            indent(out);
            out.write("[");
            _indent++;
            String comma = "";
            for (Position p : axis.getPositions())
            {
                indent(comma, out); comma = ",";
                write(p, out);
            }
            _indent--;
            indent(out);
            out.write("]");
            _indent--;
            indent(out);
            out.write("}");
        }


        void write(Position position, Writer out) throws IOException, OlapException
        {
            out.write("[");
            String comma = "";
            for (Member m : position.getMembers())
            {
                out.write(comma);
                write(m, true, out);
                comma = ",";
            }
            out.write("]");
        }


        void write(Member member, boolean withLevel, Writer out) throws IOException, OlapException
        {
            out.write("{");
//            out.write("\"dimension":{"\"name\"":" + valueToString(member.getDimension().getName()) + "},");
            out.write("\"name\":" + valueToString(member.getName()));
            out.write(",");
            out.write("\"uniqueName\":" + valueToString(member.getUniqueName()));
            int ordinal = member.getOrdinal();
            if (0 <= ordinal)
                out.write(",\"ordinal\":" + ordinal);
            if (withLevel)
            {
                out.write(",");
    //            out.write("\"hierarchy\":");
    //            writeRef(member.getHierarchy(), out);
    //            out.write(",");
                out.write("\"level\":");
                writeRef(member.getLevel(), out);
            }

            if (_includeMemberProperties)
            {
                String prefix = ",\"properties\":{";
                String suffix = "";
                for (Property p : member.getProperties())
                {
                    //                if (p instanceof Property.StandardMemberProperty)
                    //                    continue;
                    if (!p.isVisible())
                        continue;
                    Object value = member.getPropertyValue(p);
                    if (null == value)
                        continue;
                    if ((p.getUniqueName().equals("KEY") || p.getUniqueName().equals("MEMBER_CAPTION")) && member.getName().equals(value))
                        continue;
                    out.write(prefix);
                    prefix = ",";
                    suffix = "}";
                    out.write("\"" + p.getUniqueName() + "\":" + valueToString(value));
                }
                out.write(suffix);
            }

            out.write("}");
        }


        void write(Cell cell, NamedList<Property> cellProperties, Writer out) throws IOException
        {
            if (null == cellProperties || cellProperties.isEmpty())
                out.write(valueToString(cell.getValue()));
            else
            {
                out.write("{");
                out.write("\"value\":" + valueToString(cell.getValue()));
                indent(",", out);
                out.write("\"properties\":{");
                String comma="";
                for (Property p : cellProperties)
                {
                    out.write(comma); comma = ",";
                    out.write("\"" + p.getUniqueName() + "\":" + valueToString(cell.getPropertyValue(p)));
                }
                out.write("}");
//                out.write("\"coordinateList\"":[");
//                String comma = "";
//                for (int coord : cell.getCoordinateList())
//                {
//                    out.write(comma); out.write(String.valueOf(coord)); comma = ",";
//                }
//                out.write("]");
                out.write("}");
            }
        }

        String valueToString(Object v)
        {
            return JSONObject.valueToString(v);
        }
    }

    public static void convertCellSet(CellSet cs, Writer out) throws IOException, OlapException
    {
        new CellSetWriter().write(cs,out);
    }

    public static void convertCube(Cube cube, boolean includeMembers, Writer out) throws IOException
    {
        CellSetWriter w = new CellSetWriter();
        w._includeLevelMembers = includeMembers;
        w.write(cube, true, out);
    }
}
