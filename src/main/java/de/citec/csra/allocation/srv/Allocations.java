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

import de.citec.csra.rst.util.IntervalUtils;
import static de.citec.csra.rst.util.StringRepresentation.shortString;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.HUMAN;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.*;
import rst.timing.IntervalType;
import rst.timing.IntervalType.Interval;

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

	synchronized public static Allocations getInstance() {
		if (instance == null) {
			instance = new Allocations();
		}
		return instance;
	}

//	private synchronized Map<String, ResourceAllocation> getMap() {
//		synchronized (this.allocations) {
//			return new HashMap<>(this.allocations);
//		}
//	}
	synchronized boolean isAlive(String id) {
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

	synchronized State getState(String id) {
		if (this.allocations.containsKey(id)) {
			return this.allocations.get(id).getState();
		} else {
			return null;
		}
	}

	synchronized Interval getSlot(String id) {
		if (this.allocations.containsKey(id)) {
			return this.allocations.get(id).getSlot();
		} else {
			return null;
		}
	}

	synchronized ResourceAllocation get(String id) {
		if (this.allocations.containsKey(id)) {
			ResourceAllocation a = this.allocations.get(id);
			return a;
		} else {
			LOG.log(Level.FINEST, "attempt to query for allocation ''{0}'' ignored, no such allocation available", id);
		}
		return null;
	}

	synchronized ResourceAllocation setState(String id, State newState) {
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

	synchronized ResourceAllocation setReason(String id, String reason) {
		if (this.allocations.containsKey(id)) {
			ResourceAllocation current = this.allocations.get(id);

			String newDescription;
			if (current.hasDescription()) {
				String desc = current.getDescription();
				Pattern p = Pattern.compile(reason + "\\[([0-9]+)\\]");
				Matcher m = p.matcher(desc);
				if (m.find()) {
					long n = Long.valueOf(m.group(1));
					newDescription = m.replaceFirst(reason + "[" + String.valueOf(n + 1) + "]");
				} else {
					newDescription = current.getDescription() + " " + reason + "[1]";
				}
			} else {
				newDescription = reason + "[1]";
			}

			return this.allocations.put(id,
					ResourceAllocation.
							newBuilder(current).
							setDescription(newDescription).
							build());
		} else {
			LOG.log(Level.WARNING, "attempt to modify allocation ''{0}'' ignored, no such allocation available", id);
			return null;
		}
	}

	synchronized ResourceAllocation remove(String id) {
		return this.allocations.remove(id);
	}

	synchronized public boolean handle(ResourceAllocation incoming) {
		ResourceAllocation current = get(incoming.getId());
		State currentState = (current != null) ? current.getState() : null;
		State incomingState = incoming.getState();
		String incomingStr = shortString(incoming);
		String currentStr = shortString(incoming);
		switch (incomingState) {
			case REQUESTED:
				if (currentState == null) {
					LOG.log(Level.INFO,
							"Performing client-requested state transition ''{0}'' -> ''{1}'' ({2})",
							new Object[]{currentState, incomingState, incomingStr});
					return request(incoming);
				} else {
					LOG.log(Level.INFO,
							"Informing client about current allocation with id ''{0}'' ({1})",
							new Object[]{incoming.getId(), currentStr});
					return inform(incoming);
				}
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

	synchronized boolean request(ResourceAllocation allocation) {
		this.allocations.put(allocation.getId(), allocation);
		this.notifications.init(allocation.getId());

		IntervalType.Interval match = findSlot(allocation, false);
		if (match == null) {
			LOG.log(Level.FINER, "Allocation request failed (slot not available): {0}", shortString(allocation));
			reject(allocation, "slot not available");
			return false;
		} else {
			allocation = ResourceAllocation.newBuilder(allocation).setSlot(match).build();
			LOG.log(Level.FINER, "Allocation request successful: {0}", shortString(allocation));
			schedule(allocation);
			return true;
		}
	}

	synchronized boolean inform(ResourceAllocation allocation) {
		this.notifications.update(allocation.getId(), true);
		return true;
	}

	/**
	 * Modifies an already running allocation due to external request.
	 *
	 * The allocation server notifies client via RSB about the outcome: Either
	 * the modification has failed, so the old values are published again, or
	 * the modification has been (partially) successful, and the new values are
	 * published.
	 *
	 * @param allocation the {@link ResourceAllocation} containing the new
	 * values.
	 * @return whether the modification has been successful or not
	 */
	synchronized boolean modify(ResourceAllocation allocation) {
		if (isAlive(allocation.getId())) {
			IntervalType.Interval match = findSlot(allocation, false);
			if (match == null) {
				LOG.log(Level.FINER, "Allocation modification failed (slot not available): {0}", shortString(allocation));
				update(get(allocation.getId()), "slot not available", true);
				return false;
			} else {
				LOG.log(Level.FINER, "Allocation modification successful: {0}", shortString(allocation));
				allocation = ResourceAllocation.newBuilder(allocation).setSlot(match).build();
				update(allocation, "modification successful", true);
				return true;
			}
		} else {
			LOG.log(Level.WARNING, "attempt to modify allocation ''{0}'' ignored, no such allocation active", allocation.getId());
			return false;
		}
	}

	synchronized void schedule(ResourceAllocation allocation) {
		LOG.log(Level.FINE, "Scheduling: {0}", shortString(allocation));
		updateAffected(allocation, "slot superseded");
		if (isAlive(allocation.getId())) {
			this.allocations.put(allocation.getId(), allocation);
			setState(allocation.getId(), SCHEDULED);
			this.notifications.update(allocation.getId(), true);
		} else {
			LOG.log(Level.WARNING, "attempt to schedule allocation ''{0}'' ignored, no such allocation active", allocation.getId());
		}
	}

	synchronized void reject(ResourceAllocation allocation, String reason) {
		LOG.log(Level.FINE, "Rejecting: {0}", shortString(allocation));
		if (isAlive(allocation.getId())) {
			setState(allocation.getId(), REJECTED);
			setReason(allocation.getId(), reason);
			this.notifications.update(allocation.getId(), true);
			remove(allocation.getId());
		} else {
			LOG.log(Level.WARNING, "attempt to reject allocation ''{0}'' ignored, no such allocation active", allocation.getId());
		}
	}

	synchronized void update(ResourceAllocation allocation, String reason, boolean updateAffected) {
		LOG.log(Level.FINE, "Updating: {0}", shortString(allocation));
		if (updateAffected) {
			updateAffected(allocation, "slot superseded");
		}
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

	synchronized boolean finalize(ResourceAllocation allocation, String reason) {
		LOG.log(Level.FINE, "Finalizing: {0}", shortString(allocation));
		if (isAlive(allocation.getId())) {
			this.allocations.put(allocation.getId(), allocation);
			if (reason != null) {
				setReason(allocation.getId(), reason);
			}
			this.notifications.update(allocation.getId(), true);
			remove(allocation.getId());
			return true;
		} else {
			LOG.log(Level.WARNING, "attempt to release allocation ''{0}'' ignored, no such allocation active", allocation.getId());
			return false;
		}
	}

	synchronized boolean sharedPrefix(List<String> one, List<String> two) {
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

	synchronized boolean isPermitted(String one, String two) {
		Pattern ticket = Pattern.compile("^(.+)#(.+)$");
		Matcher m1 = ticket.matcher(one);
		Matcher m2 = ticket.matcher(two);
		if (m1.matches() && m2.matches()) {
			String id1 = m1.group(2);
			String id2 = m2.group(2);
			return id1.equals(id2);
		} else {
			return false;
		}
	}

	synchronized List<ResourceAllocation> getBlockers(ResourceAllocation allocation, boolean refit) {
		Map<String, ResourceAllocation> storedMap = new HashMap<>(this.allocations);
		storedMap.remove(allocation.getId());

		List<ResourceAllocation> blocking = new LinkedList<>();
		for (ResourceAllocation stored : storedMap.values()) {
			boolean permitted = isPermitted(stored.getId(), allocation.getId());
			if (!permitted) {
				boolean shared = sharedPrefix(stored.getResourceIdsList(), allocation.getResourceIdsList());
				if (shared) {
					if (stored.getPriority().compareTo(allocation.getPriority()) > 0) {
						blocking.add(stored);
					} else if (stored.getPriority().compareTo(allocation.getPriority()) == 0) {
						if (refit || allocation.getInitiator().equals(SYSTEM)) {
							blocking.add(stored);
						}
					}
				}
			}
		}

		blocking.removeIf(e -> e.getSlot().getEnd().getTime() < System.currentTimeMillis());
		blocking.sort((l, r) -> {
			return (int) (l.getSlot().getEnd().getTime() - r.getSlot().getEnd().getTime());
		});
		return blocking;
	}

	synchronized List<ResourceAllocation> getAffected(ResourceAllocation allocation) {
		Map<String, ResourceAllocation> storedMap = new HashMap<>(this.allocations);
		storedMap.remove(allocation.getId());

		List<ResourceAllocation> affected = new LinkedList<>();
		for (ResourceAllocation stored : storedMap.values()) {
			boolean shared = sharedPrefix(stored.getResourceIdsList(), allocation.getResourceIdsList());
			if (shared) {
				if (stored.getPriority().compareTo(allocation.getPriority()) < 0) {
					affected.add(stored);
				} else if (stored.getPriority().compareTo(allocation.getPriority()) == 0) {
					if (allocation.getInitiator().equals(HUMAN)) {
						affected.add(stored);
					}
				}
			}
		}

		affected.removeIf(e -> e.getSlot().getEnd().getTime() < System.currentTimeMillis());
		affected.sort((l, r) -> {
			return (int) (l.getSlot().getEnd().getTime() - r.getSlot().getEnd().getTime());
		});
		return affected;
	}

	synchronized IntervalType.Interval findSlot(ResourceAllocation allocation, boolean refit) {
		LOG.log(Level.FINE, "Fitting: {0}", shortString(allocation));
		List<ResourceAllocation> blockers = getBlockers(allocation, refit);
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
						LOG.log(Level.INFO, "Requested allocation failed (unsupported policy): {0}", shortString(allocation));
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

	synchronized void updateAffected(ResourceAllocation allocation, String reason) {
		LOG.log(Level.FINE, "Updating allocations affected by: {0}", shortString(allocation));
		List<ResourceAllocation> affected = getAffected(allocation);
		for (ResourceAllocation running : affected) {
			IntervalType.Interval mod = findSlot(running, true);
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
				update(builder.build(), reason, false);
			}
		}
	}
}
