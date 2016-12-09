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
				LOG.log(Level.FINEST, "attempt to check alive state for allocation ''{0}'' ignored, no such allocation available", id);
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
				LOG.log(Level.FINEST, "attempt to query for allocation ''{0}'' ignored, no such allocation available", id);
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

	public boolean handle(ResourceAllocation incoming) {
		ResourceAllocation current = get(incoming.getId());
		State currentState = (current != null) ? current.getState() : null;
		State incomingState = incoming.getState();
		String incomingStr = incoming.toString().replaceAll("\n", " ");

		synchronized (this.allocations) {
			switch (incomingState) {
				case REQUESTED:
					if (currentState == null) {
						LOG.log(Level.INFO,
								"Performing client-requested state transition ''{0}'' -> ''{1}'' ({2})",
								new Object[]{currentState, incomingState, incomingStr});
						return request(incoming);
					}
					break;
				case CANCELLED:
					if (currentState != null && currentState.equals(SCHEDULED)) {
						LOG.log(Level.INFO,
								"Performing client-requested state transition ''{0}'' -> ''{1}'' ({2})",
								new Object[]{currentState, incomingState, incomingStr});
						return finalize(incoming, "client request");
					}
					break;
				case ABORTED:
				case RELEASED:
					if (currentState != null && currentState.equals(ALLOCATED)) {
						LOG.log(Level.INFO,
								"Performing client-requested state transition ''{0}'' -> ''{1}'' ({2})",
								new Object[]{currentState, incomingState, incomingStr});
						return finalize(incoming, "client request");
					}
					break;
				case ALLOCATED:
				case SCHEDULED:
					if (currentState != null && currentState.equals(incoming.getState())) {
						LOG.log(Level.INFO,
								"Performing client-requested state transition ''{0}'' -> ''{1}'' ({2})",
								new Object[]{currentState, incomingState, incomingStr});
						return modify(incoming);
					}
					break;
				case REJECTED:
				default:
					break;
			}
			LOG.log(Level.WARNING,
					"Illegal client-requested state transition ''{0}'' -> ''{1}'', ignoring ({2})",
					new Object[]{currentState, incomingState, incomingStr});
			return false;
		}
	}

	boolean request(ResourceAllocation allocation) {
		synchronized (this.allocations) {
			this.allocations.put(allocation.getId(), allocation);
			this.notifications.init(allocation.getId());
		}

		IntervalType.Interval match = fit(allocation);
		if (match == null) {
			LOG.log(Level.FINER, "Allocation request failed (slot not available): {0}", allocation.toString().replaceAll("\n", " "));
			reject(allocation, "slot not available");
			return false;
		} else {
			LOG.log(Level.FINER, "Allocation request successful: {0}", allocation.toString().replaceAll("\n", " "));
			allocation = ResourceAllocation.newBuilder(allocation).setSlot(match).build();
			schedule(allocation);
			return true;
		}
	}

	boolean modify(ResourceAllocation allocation) {
		synchronized (this.allocations) {
			if (isAlive(allocation.getId())) {
				IntervalType.Interval match = fit(allocation);
				if (match == null) {
					LOG.log(Level.FINER, "Allocation modification failed (slot not available): {0}", allocation.toString().replaceAll("\n", " "));
					update(get(allocation.getId()), "slot not available");
					return false;
				} else {
					LOG.log(Level.FINER, "Allocation modification successful: {0}", allocation.toString().replaceAll("\n", " "));
					allocation = ResourceAllocation.newBuilder(allocation).setSlot(match).build();
					update(allocation, "modification successful");
					return true;
				}
			} else {
				LOG.log(Level.WARNING, "attempt to modify allocation ''{0}'' ignored, no such allocation active", allocation.getId());
				return false;
			}
		}
	}

	void schedule(ResourceAllocation allocation) {
		LOG.log(Level.FINE, "Scheduling: {0}", allocation.toString().replaceAll("\n", " "));
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
		LOG.log(Level.FINE, "Rejecting: {0}", allocation.toString().replaceAll("\n", " "));
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
		LOG.log(Level.FINE, "Updating: {0}", allocation.toString().replaceAll("\n", " "));
		synchronized (this.allocations) {
			if (isAlive(allocation.getId())) {
				this.allocations.put(allocation.getId(), allocation);
				if (reason != null) {
					setReason(allocation.getId(), reason);
				}
				this.notifications.update(allocation.getId(), true);
			} else {
				LOG.log(Level.WARNING, "attempt to update allocation ''{0}'' ignored, no such allocation active", allocation.getId());
			}
		}
	}

	boolean finalize(ResourceAllocation allocation, String reason) {
		LOG.log(Level.FINE, "Finalizing: {0}", allocation.toString().replaceAll("\n", " "));
		synchronized (this.allocations) {
			if (isAlive(allocation.getId())) {
				this.allocations.put(allocation.getId(), allocation);
				if (reason != null) {
					setReason(allocation.getId(), reason);
				}
				this.notifications.update(allocation.getId(), false);
				this.allocations.remove(allocation.getId());
				return true;
			} else {
				LOG.log(Level.WARNING, "attempt to release allocation ''{0}'' ignored, no such allocation active", allocation.getId());
				return false;
			}
		}
	}

	boolean sharedPrefix(List<String> one, List<String> two) {
		boolean contains = false;
		search:
		for (String a : one) {
			for (String b : two) {
				if (b.startsWith(a) || a.startsWith(b)) {
					contains = true;
					break search;
				}
			}
		}
		return contains;
	}

	List<ResourceAllocation> getBlockers(ResourceAllocation inc) {
		Map<String, ResourceAllocation> storedMap = getMap();
		storedMap.remove(inc.getId());
		List<ResourceAllocation> matching = new LinkedList<>();
		for (ResourceAllocation stored : storedMap.values()) {
			boolean shared = sharedPrefix(stored.getResourceIdsList(), inc.getResourceIdsList());
			if (shared) {
				switch (inc.getInitiator()) {
					case HUMAN:
						switch (inc.getState()) {
							case REQUESTED:
								if (stored.getPriority().compareTo(inc.getPriority()) > 0) {
									matching.add(stored);
								}
								break;
							case ALLOCATED:
								if (stored.getPriority().compareTo(inc.getPriority()) >= 0) {
									matching.add(stored);
								}
								break;
						}
						break;
					case SYSTEM:
						if (stored.getPriority().compareTo(inc.getPriority()) >= 0) {
							matching.add(stored);
						}
						break;
				}
			}
		}

		matching.removeIf(e -> e.getSlot().getEnd().getTime() < System.currentTimeMillis());
		matching.sort((l, r) -> {
			return (int) (l.getSlot().getEnd().getTime() - r.getSlot().getEnd().getTime());
		});
		return matching;
	}

	List<ResourceAllocation> getAffected(ResourceAllocation inc) {
		Map<String, ResourceAllocation> storedMap = getMap();
		storedMap.remove(inc.getId());
		List<ResourceAllocation> matching = new LinkedList<>();
		for (ResourceAllocation stored : storedMap.values()) {
			boolean shared = sharedPrefix(stored.getResourceIdsList(), inc.getResourceIdsList());
			if (shared) {
				switch (inc.getInitiator()) {
					case HUMAN:
						switch (inc.getState()) {
							case REQUESTED:
								if (stored.getPriority().compareTo(inc.getPriority()) <= 0) {
									matching.add(stored);
								}
								break;
							case ALLOCATED:
								if (stored.getPriority().compareTo(inc.getPriority()) < 0) {
									matching.add(stored);
								}
						}
						break;
					case SYSTEM:
						if (stored.getPriority().compareTo(inc.getPriority()) < 0) {
							matching.add(stored);
						}
						break;
				}
			}
		}

		matching.removeIf(e -> e.getSlot().getEnd().getTime() < System.currentTimeMillis());
		matching.sort((l, r) -> {
			return (int) (l.getSlot().getEnd().getTime() - r.getSlot().getEnd().getTime());
		});
		return matching;
	}

	IntervalType.Interval fit(ResourceAllocation allocation) {
		List<ResourceAllocation> blockers = getBlockers(allocation);
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
			if (allocation.getState().equals(ALLOCATED)) {
				return IntervalUtils.includeNow(allocation.getSlot());
			} else {
				return allocation.getSlot();
			}
		}
	}

	void updateAffected(ResourceAllocation allocation, String reason) {
		List<ResourceAllocation> affected = getAffected(allocation);
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
				finalize(builder.build(), reason);
			} else if (!mod.equals(running.getSlot())) {
				builder.setSlot(mod);
				update(builder.build(), reason);
			}
		}
	}
}
