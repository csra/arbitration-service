/*
 * Copyright (C) 2016 Patrick Holthaus (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
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
package de.citec.csra.allocation.srv;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ABORTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ALLOCATED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.CANCELLED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.SCHEDULED;
import rst.timing.IntervalType.Interval;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class AllocationService {

	private final static Logger LOG = Logger.getLogger(AllocationService.class.getName());

	private List<ResourceAllocation> getBlockers(String resource, String id, Priority min) {
		Map<String, ResourceAllocation> temp = Allocations.getInstance().getMap();
		temp.remove(id);
		List<ResourceAllocation> matching = temp.values().stream().
				filter(allocation
						-> allocation.getResourceIds(0).startsWith(resource)
						|| resource.startsWith(allocation.getResourceIds(0))
				).
				filter(allocation
						-> allocation.getPriority().compareTo(min) >= 0
				).
				collect(Collectors.toList());
		matching.removeIf(e -> e.getSlot().getEnd().getTime() < System.currentTimeMillis());
		matching.sort((l, r) -> {
			return (int) (l.getSlot().getEnd().getTime() - r.getSlot().getEnd().getTime());
		});
		return matching;
	}

	private List<ResourceAllocation> getAffected(String resource, String id, Priority min) {
		Map<String, ResourceAllocation> temp = Allocations.getInstance().getMap();
		temp.remove(id);
		List<ResourceAllocation> matching = temp.values().stream().
				filter(allocation
						-> allocation.getResourceIds(0).startsWith(resource)
						|| resource.startsWith(allocation.getResourceIds(0))
				).
				filter(allocation
						-> allocation.getPriority().compareTo(min) < 0
				).
				collect(Collectors.toList());
		matching.removeIf(e -> e.getSlot().getEnd().getTime() < System.currentTimeMillis());
		matching.sort((l, r) -> {
			return (int) (l.getSlot().getEnd().getTime() - r.getSlot().getEnd().getTime());
		});
		return matching;
	}

	private Interval fit(ResourceAllocation allocation) {
		List<ResourceAllocation> blockers = getBlockers(allocation.getResourceIds(0), allocation.getId(), allocation.getPriority());
		List<Interval> times = blockers.stream().map(b -> b.getSlot()).collect(Collectors.toList());
		Interval match = null;
		if (allocation.getState().equals(ALLOCATED)) {
			match = IntervalUtils.findRemaining(allocation.getSlot(), times);
		} else {
			switch (allocation.getPolicy()) {
				case PRESERVE:
					match = IntervalUtils.findComplete(allocation.getSlot(), allocation.hasConstraints() ? allocation.getConstraints() : allocation.getSlot(), times);
					break;
				case FIRST:
					match = IntervalUtils.findFirst(allocation.getSlot(), allocation.hasConstraints() ? allocation.getConstraints() : allocation.getSlot(), times);
					break;
				case MAXIMUM:

					match = IntervalUtils.findMax(allocation.getSlot(), allocation.hasConstraints() ? allocation.getConstraints() : allocation.getSlot(), times);

					break;
				default:
					LOG.log(Level.INFO, "Requested allocation failed (unsupported policy): {0}", allocation.toString().replaceAll("\n", " "));
					break;
			}
		}
		return match;
	}

	private void updateAffected(ResourceAllocation allocation, String reason) {
		List<ResourceAllocation> affected = getAffected(allocation.getResourceIds(0), allocation.getId(), allocation.getPriority());
		for (ResourceAllocation running : affected) {
			Interval mod = fit(running);
			ResourceAllocation.Builder builder = ResourceAllocation.newBuilder(running);

			if (mod == null) {
				switch (running.getState()) {
					case REQUESTED:
					case SCHEDULED:
						builder.setState(CANCELLED);
						break;
					case ALLOCATED:
						builder.setState(ABORTED);
						break;
				}
				Allocations.getInstance().update(builder.build(), reason);
			} else if (!mod.equals(running.getSlot())) {
				builder.setSlot(mod);
				Allocations.getInstance().update(builder.build(), reason);
			}
		}
	}

	public boolean requested(ResourceAllocation allocation) {
		LOG.log(Level.INFO, "Requested allocation: {0}", allocation.toString().replaceAll("\n", " "));
		Allocations.getInstance().init(allocation);

		Interval match = fit(allocation);
		if (match == null) {
			LOG.log(Level.INFO, "Requested allocation failed (slot not available): {0}", allocation.toString().replaceAll("\n", " "));
			Allocations.getInstance().reject(allocation, "no slot available");
			return false;
		} else {
			allocation = ResourceAllocation.newBuilder(allocation).setSlot(match).build();
			updateAffected(allocation, "slot superseded");
			Allocations.getInstance().schedule(allocation);
			return true;
		}
	}

	public void released(ResourceAllocation allocation) {
		Allocations.getInstance().release(allocation);
	}

}
