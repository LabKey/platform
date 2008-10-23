/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.common.tools;


import java.util.*;

/**
 * User: mbellew
 * Date: Sep 16, 2004
 * Time: 2:03:20 PM
 * <p/>
 * Dumb implementation of 2D tree, find good open source implementation, or get back to this
 * <p/>
 * Not thead safe
 */
public class Tree2D
	{
	ArrayList entries = new ArrayList();
	Entry2D[] xArray = null;
	Entry2D[] yArray = null;
	TreeSet xTree = new TreeSet(Entry2D.compareX);
	TreeSet yTree = new TreeSet(Entry2D.compareY);

	static class Entry2D
		{
		float x;
		float y;
		int i;

		Entry2D(float x, float y, int i)
			{
			this.x = x;
			this.y = y;
			this.i = i;
			}

		static Comparator compareX = new Comparator()
			{
			public int compare(Object o1, Object o2)
				{
				Entry2D e1 = (Entry2D)o1;
				Entry2D e2 = (Entry2D)o2;
				return e1.x > e2.x ? 1 : e1.x < e2.x ? -1 :
				        e1.i > e2.i ? 1 : e1.i < e2.i ? -1 : 0;
				}
			};
		static Comparator compareY = new Comparator()
			{
			public int compare(Object o1, Object o2)
				{
				Entry2D e1 = (Entry2D)o1;
				Entry2D e2 = (Entry2D)o2;
				return e1.y > e2.y ? 1 : e1.y < e2.y ? -1 :
				        e1.i > e2.i ? 1 : e1.i < e2.i ? -1 : 0;
				}
			};
		}

	static class IndexRange
		{
		int start, end;
		}


	public Tree2D()
		{
		}


	public void add(float x, float y, Object o)
		{
		int i = entries.size();
		entries.add(o);
		Entry2D entry = new Entry2D(x, y, i);
		xTree.add(entry);
		yTree.add(entry);
//		bitSet = null;
		xArray = null;
		yArray = null;
		}


	public ArrayList getPoints(float xMin, float yMin, float xMax, float yMax)
		{
		return getPoints(xMin, yMin, xMax, yMax, new ArrayList());
		}


	/** min inclusive, max exclusive */
	public ArrayList getPoints(float xMin, float yMin, float xMax, float yMax, ArrayList list)
		{
		list.clear();
		Entry2D[] xEntries = getXEntries();
		Entry2D[] yEntries = getYEntries();

		IndexRange xRange = find(xEntries, xMin, xMax, Entry2D.compareX);
		IndexRange yRange = find(yEntries, yMin, yMax, Entry2D.compareY);

		if (xRange.end - xRange.start < yRange.end - yRange.start)
			{
			for (int x = xRange.start; x < xRange.end; x++)
				{
				Entry2D e = xEntries[x];
				if (e.y >= yMin && e.y <= yMax)
					list.add(entries.get(e.i));
				}
			}
		else
			{
			for (int y = yRange.start; y < yRange.end; y++)
				{
				Entry2D e = yEntries[y];
				if (e.x >= xMin && e.x <= xMax)
					list.add(entries.get(e.i));
				}
			}
		return list;
		}


	private IndexRange find(Entry2D[] entries, float min, float max, Comparator c)
		{
		IndexRange range = new IndexRange();
		// it's irritating, but need to provide matching object class here
		Entry2D finder = new Entry2D(min, min, 0);
		range.start = Arrays.binarySearch(entries, finder, c);
		if (range.start < 0)
			range.start = -(range.start+1);

		finder.x = max; finder.y = max;
		range.end = Arrays.binarySearch(entries, finder, c);
		if (range.end < 0)
			range.end = -(range.end+1);
        return range;
		}


	public boolean containsPoints(float xMin, float yMin, float xMax, float yMax)
		{
		// optimize
		ArrayList list = getPoints(xMin, yMin, xMax, yMax);
		return !list.isEmpty();
		}


	private Entry2D[] getXEntries()
		{
		if (null == xArray)
			xArray = (Entry2D[])xTree.toArray(new Entry2D[xTree.size()]);
		return xArray;
		}


	private Entry2D[] getYEntries()
		{
		if (null == yArray)
			yArray = (Entry2D[])yTree.toArray(new Entry2D[yTree.size()]);
		return yArray;
		}


//	private BitSet getBitSet()
//		{
//		if (null == bitSet)
//			bitSet = new BitSet(entries.size());
//		else
//			bitSet.clear();
//		return bitSet;
//		}
	}
