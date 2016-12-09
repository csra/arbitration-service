/* 
 * Copyright (C) 2016 Bielefeld University, Patrick Holthaus
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

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.time4j.Moment;
import net.time4j.range.ChronoInterval;
import net.time4j.range.IntervalCollection;
import net.time4j.range.MomentInterval;
import rst.communicationpatterns.ResourceAllocationType;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.timing.IntervalType;
import rst.timing.IntervalType.Interval;
import rst.timing.TimestampType;
import rst.timing.TimestampType.Timestamp;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class IntervalUtils {

	private final static class LengthComparator implements Comparator<ChronoInterval<Moment>> {

		@Override
		public int compare(ChronoInterval<Moment> o1, ChronoInterval<Moment> o2) {
			MomentInterval m1 = (MomentInterval) o1;
			MomentInterval m2 = (MomentInterval) o2;
			long length1 = m1.getRealDuration().getSeconds() * 1000000000 + m1.getRealDuration().getFraction();
			long length2 = m2.getRealDuration().getSeconds() * 1000000000 + m2.getRealDuration().getFraction();
			return (int) (length1 - length2);
		}
	}

	public static Interval buildRelativeRst(long delay, long duration) {
		long now = System.currentTimeMillis();
		long start = now + delay;
		long end = start + duration;
		return buildRst(start, end);
	}

	public static Interval buildRst(long begin, long end) {
		return Interval.newBuilder().
				setBegin(Timestamp.newBuilder().setTime(begin)).
				setEnd(Timestamp.newBuilder().setTime(end)).build();
	}

	private final static Comparator<ChronoInterval<Moment>> BY_LENGTH = new LengthComparator();
	private final static Comparator<ChronoInterval<Moment>> BY_BEGIN = MomentInterval.comparator();

	public static MomentInterval fromRst(Interval i) {
		if (i != null) {
			return MomentInterval.between(Instant.ofEpochMilli(i.getBegin().getTime()), Instant.ofEpochMilli(i.getEnd().getTime()));
		} else {
			return null;
		}
	}

	public static List<MomentInterval> fromRst(List<Interval> i) {
		return i.stream().map(e -> fromRst(e)).collect(Collectors.toList());
	}

	public static Interval fromT4j(MomentInterval i) {
		if (i != null) {
			return Interval.newBuilder().
					setBegin(Timestamp.newBuilder().setTime(i.getStartAsInstant().toEpochMilli())).
					setEnd(Timestamp.newBuilder().setTime(i.getEndAsInstant().toEpochMilli())).
					build();
		} else {
			return null;
		}
	}

	public static List<Interval> fromT4j(List<MomentInterval> i) {
		return i.stream().map(e -> fromT4j(e)).collect(Collectors.toList());
	}

	public static Interval findRemaining(Interval goal, List<Interval> blocks) {
		return fromT4j(findRemaining(fromRst(goal), fromRst(blocks)));
	}

	public static Interval findFirst(Interval goal, Interval range, List<Interval> blocks) {
		return fromT4j(findFirst(fromRst(goal), fromRst(range), fromRst(blocks)));
	}

	public static Interval findMax(Interval goal, Interval range, List<Interval> blocks) {
		return fromT4j(findMax(fromRst(goal), fromRst(range), fromRst(blocks)));
	}

	public static Interval findComplete(Interval goal, Interval range, List<Interval> blocks) {
		return fromT4j(findComplete(fromRst(goal), fromRst(range), fromRst(blocks)));
	}

	public static Interval includeNow(Interval goal) {
		return fromT4j(includeNow(fromRst(goal)));
	}

	public static MomentInterval findFirst(MomentInterval g, MomentInterval r, List<MomentInterval> b) {

		IntervalCollection<Moment> blocking = IntervalCollection.onMomentAxis().plus(b);
		IntervalCollection<Moment> goal = IntervalCollection.onMomentAxis().plus(g);
		IntervalCollection<Moment> free = IntervalCollection.onMomentAxis().plus(blocking.withComplement(r).getIntervals());
		IntervalCollection<Moment> overlaps = free.intersect(goal);

		if (!overlaps.isEmpty()) {
			return (MomentInterval) overlaps.getIntervals().stream().min(BY_BEGIN).get();
		}
		if (!free.isEmpty()) {
			return (MomentInterval) free.getIntervals().stream().min(BY_BEGIN).get();
		}
		return null;
	}

	public static MomentInterval findMax(MomentInterval g, MomentInterval r, List<MomentInterval> b) {

		IntervalCollection<Moment> blocking = IntervalCollection.onMomentAxis().plus(b);
		IntervalCollection<Moment> goal = IntervalCollection.onMomentAxis().plus(g);
		IntervalCollection<Moment> free = IntervalCollection.onMomentAxis().plus(blocking.withComplement(r).getIntervals());
		IntervalCollection<Moment> overlaps = free.intersect(goal);

		if (!overlaps.isEmpty()) {
			return (MomentInterval) overlaps.getIntervals().stream().max(BY_LENGTH).get();
		}
		if (!free.isEmpty()) {
			return (MomentInterval) free.getIntervals().stream().max(BY_LENGTH).get();
		}
		return null;
	}

	public static MomentInterval findRemaining(MomentInterval g, List<MomentInterval> b) {

		Moment now = Moment.from(Instant.now());
		IntervalCollection<Moment> blocking = IntervalCollection.onMomentAxis().plus(b);

		for (ChronoInterval<Moment> m : blocking.getIntervals()) {
			if (m.contains(now)) {
				return null;
			}
		}

		IntervalCollection<Moment> goal = IntervalCollection.onMomentAxis().plus(g);
		IntervalCollection<Moment> free = IntervalCollection.onMomentAxis().plus(blocking.withComplement(g).getIntervals());
		IntervalCollection<Moment> overlaps = free.intersect(goal);

		if (!overlaps.isEmpty()) {
			return includeNow((MomentInterval) overlaps.getIntervals().stream().min(BY_BEGIN).get());
		}
		return null;
	}

	public static MomentInterval findComplete(MomentInterval g, MomentInterval r, List<MomentInterval> b) {

		IntervalCollection<Moment> blocking = IntervalCollection.onMomentAxis().plus(b);
		IntervalCollection<Moment> goal = IntervalCollection.onMomentAxis().plus(g);
		IntervalCollection<Moment> free = IntervalCollection.onMomentAxis().plus(blocking.withComplement(r).getIntervals());
		IntervalCollection<Moment> overlaps = free.intersect(goal);

		if (!overlaps.isEmpty()) {
			MomentInterval first = (MomentInterval) overlaps.getIntervals().get(0);
			if (first.equivalentTo(g)) {
				return first;
			}
		}
		return null;
	}

	public static MomentInterval includeNow(MomentInterval g) {
		Moment now = Moment.from(Instant.now());
		if (g.contains(now)) {
			return g;
		} else {
			if (g.isAfter(now)) {
				return g.withStart(now);
			} else {
				return g.withEnd(now);
			}
		}
	}

	@Deprecated
	public static ResourceAllocation shift(ResourceAllocation prototype, long start, long end) {
		IntervalType.Interval.Builder interval = IntervalType.Interval.newBuilder().
				setBegin(TimestampType.Timestamp.newBuilder().setTime(start)).
				setEnd(TimestampType.Timestamp.newBuilder().setTime(end));

		return ResourceAllocationType.ResourceAllocation.newBuilder(prototype).
				setSlot(interval).build();
	}

	@Deprecated
	public static ResourceAllocation shorten(ResourceAllocation prototype, long cStart, long cEnd) {
		long myStart = prototype.getSlot().getBegin().getTime();
		long myEnd = prototype.getSlot().getEnd().getTime();

		if (cEnd <= myEnd) {
			// conflict ends before our interval
			if (cEnd >= myStart) {
				if (cStart >= myStart) {
					// |-------|
					//    |--|
					// |-|
					// conflict starts and ends inside our interval
					myEnd = cStart - 1;
				} else {
					//   |-------|
					// |---|
					//      |----|
					// conflict starts befour our interval and ends inside our interval
					myStart = cEnd + 1;
				}
			} else {
				//       |-----|
				// |---|
				//       |-----|
				// conflict is completely before our interval
			}
		} else {
			// conflict ends after our interval
			if (cStart < myStart) {
				//   |-----|
				// |----------|
				//       
				// our interval is completely contained
				return null;
			} else {
				//       |-----|
				//           |---|
				//       |--|
				// conflict starts inside our interval
				myEnd = cEnd - 1;
			}
		}
		return shift(prototype, myStart, myEnd);
	}
}
