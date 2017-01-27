/*
 * Copyright (C) 2017 pholthau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.citec.csra.allocation;

import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.timing.IntervalType.Interval;

/**
 *
 * @author pholthau
 */
public class AllocationUtils {

	public static String shortString(ResourceAllocation a) {
		if (a == null) {
			return "null";
		}
		long now = System.currentTimeMillis();
		StringBuilder b = new StringBuilder("[");
		b.append(a.getId()).append(":")
				.append(a.getState()).append(",")
				.append(a.getPriority()).append(",")
				.append(a.getPolicy()).append(",")
				.append(a.getInitiator()).append(",")
				.append("(")
				.append(a.getSlot().getBegin().getTime() - now)
				.append(")-(")
				.append(a.getSlot().getEnd().getTime() - now)
				.append(")");
		if (a.getResourceIdsCount() > 0) {
			b.append(";");
			for (String s : a.getResourceIdsList()) {
				b.append(s).append(",");
			}
			b.deleteCharAt(b.length() - 1);
		}
		b.append("]");
		return b.toString();
	}

	public static String shortTime(Interval i) {
		if (i == null) {
			return "null";
		}
		long now = System.currentTimeMillis();
		StringBuilder b = new StringBuilder("(");
		b.append(i.getBegin().getTime() - now)
				.append(")-(")
				.append(i.getEnd().getTime() - now)
				.append(")");
		return b.toString();
	}
}
