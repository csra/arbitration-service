/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.allocation.srv;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.*;
import rst.timing.IntervalType;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class Allocations {

	private static Allocations instance;
	private final Map<String, ResourceAllocation> allocations;
	private final NotificationService notifications;

	private final static Logger LOG = Logger.getLogger(Allocations.class.getName());

	private Allocations() {
		this.allocations = new ConcurrentHashMap<>();
		this.notifications = NotificationService.getInstance();
	}

	public static Allocations getInstance() {
		if (instance == null) {
			instance = new Allocations();
		}
		return instance;
	}

	Map<String, ResourceAllocation> getMap() {
		synchronized (this.allocations) {
			return new HashMap<>(this.allocations);
		}
	}

	boolean isAlive(String id) {
		synchronized (this.allocations) {
			if (this.allocations.containsKey(id)) {
				ResourceAllocation a = this.allocations.get(id);
				State s = a.getState();
				switch (s) {
					case REJECTED:
					case CANCELLED:
					case ABORTED:
					case RELEASED:
						return false;
					case ALLOCATED:
					case REQUESTED:
					case SCHEDULED:
					default:
						return true;
				}
			} else {
				LOG.log(Level.WARNING, "attempt to check alive state for allocation ''{0}'' ignored, no such allocation available", id);
			}
			return false;
		}
	}

	ResourceAllocation get(String id) {
		synchronized (this.allocations) {
			if (this.allocations.containsKey(id)) {
				ResourceAllocation a = this.allocations.get(id);
				return a;
			} else {
				LOG.log(Level.WARNING, "attempt to query for allocation ''{0}'' ignored, no such allocation available", id);
			}
			return null;
		}
	}

	ResourceAllocation setState(String id, State newState) {
		synchronized (this.allocations) {
			if (this.allocations.containsKey(id)) {
				return this.allocations.put(id,
						ResourceAllocation.
						newBuilder(this.allocations.get(id)).
						setState(newState).
						build());
			} else {
				LOG.log(Level.WARNING, "attempt to modify allocation ''{0}'' ignored, no such allocation available", id);
				return null;
			}
		}
	}

	ResourceAllocation setReason(String id, String reason) {
		synchronized (this.allocations) {
			if (this.allocations.containsKey(id)) {
				return this.allocations.put(id,
						ResourceAllocation.
						newBuilder(this.allocations.get(id)).
						setDescription(this.allocations.get(id).getDescription() + ": " + reason).
						build());
			} else {
				LOG.log(Level.WARNING, "attempt to modify allocation ''{0}'' ignored, no such allocation available", id);
				return null;
			}
		}
	}

	public boolean request(ResourceAllocation allocation) {
		LOG.log(Level.INFO, "Allocation requested: {0}", allocation.toString().replaceAll("\n", " "));

		synchronized (this.allocations) {
			this.allocations.put(allocation.getId(), allocation);
			this.notifications.init(allocation.getId());
		}

		IntervalType.Interval match = fit(allocation);
		if (match == null) {
			LOG.log(Level.INFO, "Requested allocation failed (slot not available): {0}", allocation.toString().replaceAll("\n", " "));
			reject(allocation, "no slot available");
			return false;
		} else {
			allocation = ResourceAllocation.newBuilder(allocation).setSlot(match).build();
			schedule(allocation);
			return true;
		}
	}

	void schedule(ResourceAllocation allocation) {
		updateAffected(allocation, "slot superseded");
		synchronized (this.allocations) {
			if (isAlive(allocation.getId())) {
				this.allocations.put(allocation.getId(), allocation);
				setState(allocation.getId(), SCHEDULED);
				this.notifications.update(allocation.getId(), true);
			} else {
				LOG.log(Level.WARNING, "attempt to schedule allocation ''{0}'' ignored, no such allocation active", allocation.getId());
			}
		}
	}

	void reject(ResourceAllocation allocation, String reason) {
		synchronized (this.allocations) {
			if (isAlive(allocation.getId())) {
				setState(allocation.getId(), REJECTED);
				setReason(allocation.getId(), reason);
				this.notifications.update(allocation.getId(), true);
				this.allocations.remove(allocation.getId());
			} else {
				LOG.log(Level.WARNING, "attempt to reject allocation ''{0}'' ignored, no such allocation active", allocation.getId());
			}
		}
	}

	void update(ResourceAllocation allocation, String reason) {
		synchronized (this.allocations) {
			if (isAlive(allocation.getId())) {
				this.allocations.put(allocation.getId(), allocation);
				setReason(allocation.getId(), reason);
				this.notifications.update(allocation.getId(), true);
				this.allocations.remove(allocation.getId());
			} else {
				LOG.log(Level.WARNING, "attempt to update allocation ''{0}'' ignored, no such allocation active", allocation.getId());
			}
		}
	}

	public void finalize(ResourceAllocation allocation) {
		synchronized (this.allocations) {
			if (isAlive(allocation.getId())) {
				this.allocations.put(allocation.getId(), allocation);
				this.notifications.update(allocation.getId(), false);
				this.allocations.remove(allocation.getId());
			} else {
				LOG.log(Level.WARNING, "attempt to release allocation ''{0}'' ignored, no such allocation active", allocation.getId());
			}
		}
	}

	List<ResourceAllocation> getBlockers(List<String> resources, String id, ResourceAllocation.Priority min) {
		Map<String, ResourceAllocation> temp = getMap();
		temp.remove(id);
		List<ResourceAllocation> matching = new LinkedList<>();
		for (ResourceAllocation r : temp.values()) {
			List<String> res = new LinkedList<>(r.getResourceIdsList());
			res.retainAll(resources);
			if (!res.isEmpty() && r.getPriority().compareTo(min) >= 0) {
				matching.add(r);
			}
		}
//		List<ResourceAllocation> matching = temp.values().stream().
//				filter(allocation
//						-> allocation.getResourceIds(0).startsWith(resource)
//						|| resource.startsWith(allocation.getResourceIds(0))
//				).
//				filter(allocation
//						-> allocation.getPriority().compareTo(min) >= 0
//				).
//				collect(Collectors.toList());
		matching.removeIf(e -> e.getSlot().getEnd().getTime() < System.currentTimeMillis());
		matching.sort((l, r) -> {
			return (int) (l.getSlot().getEnd().getTime() - r.getSlot().getEnd().getTime());
		});
		return matching;
	}

	List<ResourceAllocation> getAffected(List<String> resources, String id, ResourceAllocation.Priority min) {
		Map<String, ResourceAllocation> temp = getMap();
		temp.remove(id);
		List<ResourceAllocation> matching = new LinkedList<>();
		for (ResourceAllocation r : temp.values()) {
			List<String> res = new LinkedList<>(r.getResourceIdsList());
			res.retainAll(resources);
			if (!res.isEmpty() && r.getPriority().compareTo(min) < 0) {
				matching.add(r);
			}
		}

//		List<ResourceAllocation> matching = temp.values().stream().
//				filter(allocation
//						-> allocation.getResourceIds(0).startsWith(resource)
//						|| resource.startsWith(allocation.getResourceIds(0))
//				).
//				filter(allocation
//						-> allocation.getPriority().compareTo(min) < 0
//				).
//				collect(Collectors.toList());
		matching.removeIf(e -> e.getSlot().getEnd().getTime() < System.currentTimeMillis());
		matching.sort((l, r) -> {
			return (int) (l.getSlot().getEnd().getTime() - r.getSlot().getEnd().getTime());
		});
		return matching;
	}

	IntervalType.Interval fit(ResourceAllocation allocation) {
		List<ResourceAllocation> blockers = getBlockers(allocation.getResourceIdsList(), allocation.getId(), allocation.getPriority());
		if (!blockers.isEmpty()) {
			List<IntervalType.Interval> times = blockers.stream().map(b -> b.getSlot()).collect(Collectors.toList());
			IntervalType.Interval match = null;
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
		} else {
			return allocation.getSlot();
		}
	}

	void updateAffected(ResourceAllocation allocation, String reason) {
		List<ResourceAllocation> affected = getAffected(allocation.getResourceIdsList(), allocation.getId(), allocation.getPriority());
		for (ResourceAllocation running : affected) {
			IntervalType.Interval mod = fit(running);
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
				update(builder.build(), reason);
			} else if (!mod.equals(running.getSlot())) {
				builder.setSlot(mod);
				update(builder.build(), reason);
			}
		}
	}
}
