/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.allocation.srv;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.*;

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

	public Map<String, ResourceAllocation> getMap() {
		return new HashMap<>(this.allocations);
	}

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
			LOG.log(Level.WARNING, "attempt to check alive state for allocation ''{0}'' ignored, no such allocation available", id);
		}
		return false;
	}

	synchronized ResourceAllocation get(String id) {
		if (this.allocations.containsKey(id)) {
			ResourceAllocation a = this.allocations.get(id);
			return a;
		} else {
			LOG.log(Level.WARNING, "attempt to query for allocation ''{0}'' ignored, no such allocation available", id);
		}
		return null;
	}

	synchronized void setState(String id, State newState) {
		if (this.allocations.containsKey(id)) {
			this.allocations.put(id,
					ResourceAllocation.
					newBuilder(this.allocations.get(id)).
					setState(newState).
					build());
		} else {
			LOG.log(Level.WARNING, "attempt to modify allocation ''{0}'' ignored, no such allocation available", id);
		}
	}

	synchronized void setReason(String id, String reason) {
		if (this.allocations.containsKey(id)) {
			this.allocations.put(id,
					ResourceAllocation.
					newBuilder(this.allocations.get(id)).
					setDescription(this.allocations.get(id).getDescription() + ": " + reason).
					build());
		} else {
			LOG.log(Level.WARNING, "attempt to modify allocation ''{0}'' ignored, no such allocation available", id);
		}
	}

	synchronized void init(ResourceAllocation allocation) {
		this.allocations.put(allocation.getId(), allocation);
		this.notifications.init(allocation.getId());
	}

	synchronized void schedule(ResourceAllocation allocation) {
		if (this.allocations.containsKey(allocation.getId())) {
			setState(allocation.getId(), SCHEDULED);
			this.notifications.update(allocation.getId());
		} else {
			LOG.log(Level.WARNING, "attempt to schedule allocation ''{0}'' ignored, no such allocation available", allocation.getId());
		}
	}

	synchronized void reject(ResourceAllocation allocation, String reason) {
		if (this.allocations.containsKey(allocation.getId())) {
			setState(allocation.getId(), REJECTED);
			setReason(allocation.getId(), reason);
			this.notifications.update(allocation.getId());
			this.allocations.remove(allocation.getId());
		} else {
			LOG.log(Level.WARNING, "attempt to reject allocation ''{0}'' ignored, no such allocation available", allocation.getId());
		}
	}

	synchronized void update(ResourceAllocation allocation, String reason) {
		if (this.allocations.containsKey(allocation.getId())) {
			this.allocations.put(allocation.getId(), allocation);
			setReason(allocation.getId(), reason);
			this.notifications.update(allocation.getId());
			if (!isAlive(allocation.getId())) {
				this.allocations.remove(allocation.getId());
			}
		} else {
			LOG.log(Level.WARNING, "attempt to update allocation ''{0}'' ignored, no such allocation available", allocation.getId());
		}
	}

	synchronized void release(ResourceAllocation allocation) {
		if (this.allocations.containsKey(allocation.getId())) {
			setState(allocation.getId(), RELEASED);
			this.notifications.update(allocation.getId());
			this.allocations.remove(allocation.getId());
		} else {
			LOG.log(Level.WARNING, "attempt to release allocation ''{0}'' ignored, no such allocation available", allocation.getId());
		}
	}
}
