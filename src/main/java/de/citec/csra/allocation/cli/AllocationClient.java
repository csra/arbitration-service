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
package de.citec.csra.allocation.cli;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.InitializeException;
import rsb.RSBException;
import rsb.util.QueueAdapter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.*;
import rst.timing.IntervalType.Interval;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class AllocationClient implements SchedulerController {

	private final static Logger LOG = Logger.getLogger(AllocationClient.class.getName());

	private ResourceAllocation allocation;
	private final QueueAdapter qa;
	private final BlockingQueue<ResourceAllocation> queue;
	private final Set<SchedulerListener> listeners;
	private final RemoteAllocationService remoteService;

	public AllocationClient(ResourceAllocation allocation) throws InitializeException, RSBException {
		this.qa = new QueueAdapter();
		this.queue = qa.getQueue();
		this.allocation = allocation;
		this.listeners = new HashSet<>();
		this.remoteService = RemoteAllocationService.getInstance();
	}

	public void addSchedulerListener(SchedulerListener l) {
		this.listeners.add(l);
	}

	public void removeSchedulerListener(SchedulerListener l) {
		this.listeners.remove(l);
	}

	private synchronized boolean isAlive() {
		switch (this.allocation.getState()) {
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
	}

	@Override
	public void schedule() throws RSBException {
		LOG.log(Level.FINE,
				"resource allocation scheduled by client: ''{0}''",
				allocation.toString().replaceAll("\n", " "));
		new Thread(() -> {
			while (isAlive()) {
				try {
					ResourceAllocation update = queue.poll(2000, TimeUnit.MILLISECONDS);
					if (update != null && update.getId().equals(allocation.getId())) {
						remoteUpdated(update);
					}
				} catch (InterruptedException ex) {
					LOG.log(Level.SEVERE, "Event dispatching interrupted", ex);
					Thread.currentThread().interrupt();
					return;
				}
			}
		}).start();
		try {
			this.remoteService.addHandler(this.qa, true);
			this.remoteService.update(this.allocation);
		} catch (InterruptedException ex) {
			LOG.log(Level.SEVERE, "Could not add handler, skipping remote update", ex);
		}
	}

	@Override
	public void abort() throws RSBException {
		updateState(ABORTED);
	}

	@Override
	public void release() throws RSBException {
		updateState(RELEASED);
	}

	@Override
	public void cancel() throws RSBException {
		updateState(CANCELLED);
	}

	public void updateSlot(Interval interval) throws RSBException {
		if (isAlive()) {
			ResourceAllocation update = ResourceAllocation.newBuilder(this.allocation).setSlot(interval).build();
			this.allocation = update;
			if(!this.allocation.getState().equals(REQUESTED)){
				LOG.log(Level.FINE,
					"attempting client allocation slot change ''{0}'' -> ''{1}'' ({2})",
					new Object[]{
						allocation.getSlot().toString().replaceAll("\n", " "),
						interval.toString().replaceAll("\n", " "),
						update.toString().replaceAll("\n", " ")});
				this.remoteService.update(this.allocation);
			}
		} else {
			LOG.log(Level.FINE,
					"resource allocation not active anymore ({0}), skipping client allocation slot change ({1}) for: ''{2}''",
					new Object[]{allocation.getState(), interval.toString().replaceAll("\n", " "), allocation.toString().replaceAll("\n", " ")});
		}
	}

	private void updateState(State newState) throws RSBException {
		if (isAlive()) {
			ResourceAllocation update = ResourceAllocation.newBuilder(this.allocation).setState(newState).build();
			try {
				switch (newState) {
					case ABORTED:
					case CANCELLED:
					case RELEASED:
						LOG.log(Level.FINE,
								"attempting client allocation state change ''{0}'' -> ''{1}'' ({2})",
								new Object[]{
									allocation.getState(),
									newState,
									update.toString().replaceAll("\n", " ")});
						this.allocation = update;
						this.remoteService.removeHandler(this.qa, true);
						this.remoteService.update(this.allocation);
						break;
					case REJECTED:
					case ALLOCATED:
					case SCHEDULED:
					case REQUESTED:
						LOG.log(Level.WARNING,
								"Illegal state ({0}) , skipping remote update",
								newState);
						break;
				}

			} catch (InterruptedException ex) {
				LOG.log(Level.SEVERE,
						"Could not remove handler, skipping remote update",
						ex);
			}
		} else {
			LOG.log(Level.FINE,
					"resource allocation not active anymore ({0}), skipping client allocation state change ({1}) for: ''{2}''",
					new Object[]{allocation.getState(), newState, allocation.toString().replaceAll("\n", " ")});
		}
	}

	private void remoteUpdated(ResourceAllocation update) {
		if (isAlive()) {
			LOG.log(Level.FINE,
					"resource allocation updated by server ''{0}'' -> ''{1}'' ({2})",
					new Object[]{
						this.allocation.getState(),
						update.getState(),
						update.toString().replaceAll("\n", " ")});
			this.allocation = update;
			for (SchedulerListener l : this.listeners) {
				l.allocationUpdated(allocation, allocation.getDescription());
			}

			if (!isAlive()) {
				try {
					this.remoteService.removeHandler(this.qa, true);
				} catch (InterruptedException | RSBException ex) {
					LOG.log(Level.SEVERE, "Could not remove handler", ex);
				}
			}
		} else {
			LOG.log(Level.FINE,
					"resource allocation not active anymore ({0}), server update skipped: ''{1}''",
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName()
				+ ((this.allocation == null)
						? ""
						: "[" + this.allocation.toString().replaceAll("\n", " ") + "]");
	}
}
