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
package de.citec.csra.allocation;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.InitializeException;
import rsb.RSBException;
import rsb.util.QueueAdapter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.*;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class AllocationClient implements SchedulerController {
	
	private final static Logger LOG = Logger.getLogger(AllocationClient.class.getName());

	private ResourceAllocation allocation;
	private final BlockingQueue<ResourceAllocation> queue;
	private final Set<SchedulerListener> listeners;
	private final RemoteAllocationService remoteService;

	public AllocationClient(ResourceAllocation allocation) throws InitializeException, InterruptedException, RSBException {
		QueueAdapter qa = new QueueAdapter();
		this.allocation = allocation;
		this.queue = qa.getQueue();
		this.listeners = new HashSet<>();
		this.remoteService = RemoteAllocationService.getInstance();
		this.remoteService.addHandler(qa, true);
	}

	public void addSchedulerListener(SchedulerListener l) {
		this.listeners.add(l);
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
					ResourceAllocation update = queue.take();
					if (allocation != null && update.getId().equals(allocation.getId())) {
						switch (update.getState()) {
							case SCHEDULED:
								remoteScheduled(update);
								break;
							case ALLOCATED:
								remoteAllocated(update);
								break;
							case REJECTED:
								remoteRejected(update, update.getDescription());
								break;
							case CANCELLED:
								remoteCancelled(update, update.getDescription());
								break;
							case ABORTED:
								remoteAborted(update, update.getDescription());
							case RELEASED:
								remoteReleased(update);
								break;
							case REQUESTED:
								break;
						}
					}
				} catch (InterruptedException ex) {
					LOG.log(Level.SEVERE, "Event dispatching interrupted", ex);
					Thread.currentThread().interrupt();
					return;
				}
			}
		}).start();
		this.remoteService.update(this.allocation);
	}

	@Override
	public void abort() throws RSBException {
		ResourceAllocation update = ResourceAllocation.newBuilder(this.allocation).setState(ABORTED).build();
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE, 
					"resource allocation aborted by client: ''{0}''",
					allocation.toString().replaceAll("\n", " "));
			this.remoteService.update(this.allocation);
		} else {
			LOG.log(Level.FINE, 
					"resource allocation not active anymore ({0}), client aborting skipped: ''{1}''",
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	@Override
	public void release() throws RSBException {
		ResourceAllocation update = ResourceAllocation.newBuilder(this.allocation).setState(RELEASED).build();
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE, 
					"resource allocation released by client: ''{0}''", 
					allocation.toString().replaceAll("\n", " "));
			this.remoteService.update(this.allocation);
		} else {
			LOG.log(Level.FINE, 
					"resource allocation not active anymore ({0}), client releasing skipped: ''{1}''",
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	@Override
	public void cancel() throws RSBException {
		ResourceAllocation update = ResourceAllocation.newBuilder(this.allocation).setState(CANCELLED).build();
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE,
					"resource allocation cancelled by client: ''{0}''",
					allocation.toString().replaceAll("\n", " "));
			this.remoteService.update(this.allocation);
		} else {
			LOG.log(Level.FINE, 
					"resource allocation not active anymore ({0}), client cancelling skipped: ''{1}''",
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	private void remoteScheduled(ResourceAllocation update) {
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE, 
					"resource allocation scheduled by server: ''{0}''",
					allocation.toString().replaceAll("\n", " "));
			for (SchedulerListener l : this.listeners) {
				l.scheduled(allocation);
			}
		} else {
			LOG.log(Level.FINE, 
					"resource allocation not active anymore ({0}), server scheduling skipped: ''{1}''",
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	private void remoteRejected(ResourceAllocation update, String cause) {
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE,
					"resource allocation rejected by server: ''{0}''",
					allocation.toString().replaceAll("\n", " "));
			for (SchedulerListener l : this.listeners) {
				l.rejected(allocation, cause);
			}
		} else {
			LOG.log(Level.FINE,
					"resource allocation not active anymore ({0}), server rejecting skipped: ''{1}''",
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	private void remoteAllocated(ResourceAllocation update) {
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE,
					"resource allocation granted by server: ''{0}''",
					allocation.toString().replaceAll("\n", " "));
			for (SchedulerListener l : this.listeners) {
				l.allocated(allocation);
			}
		} else {
			LOG.log(Level.FINE, 
					"resource allocation not active anymore ({0}), server allocating skipped: ''{1}''", 
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	private void remoteAborted(ResourceAllocation update, String cause) {
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE, 
					"resource allocation aborted by server: ''{0}''",
					allocation.toString().replaceAll("\n", " "));
			for (SchedulerListener l : this.listeners) {
				l.aborted(allocation, cause);
			}
		} else {
			LOG.log(Level.FINE,
					"resource allocation not active anymore ({0}), server aborting skipped: ''{1}''",
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	private void remoteCancelled(ResourceAllocation update, String cause) {
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE,
					"resource allocation cancelled by server: ''{0}''", 
					allocation.toString().replaceAll("\n", " "));
			for (SchedulerListener l : this.listeners) {
				l.cancelled(allocation, cause);
			}
		} else {
			LOG.log(Level.FINE,
					"resource allocation not active anymore ({0}), server cancelling skipped: ''{1}''", 
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	private void remoteReleased(ResourceAllocation update) {
		if (isAlive()) {
			this.allocation = update;
			LOG.log(Level.FINE, 
					"resource allocation released by server: ''{0}''",
					allocation.toString().replaceAll("\n", " "));
			for (SchedulerListener l : this.listeners) {
				l.released(allocation);
			}
		} else {
			LOG.log(Level.FINE, 
					"resource allocation not active anymore ({0}), server releasing skipped: ''{1}''",
					new Object[]{allocation.getState(), update.toString().replaceAll("\n", " ")});
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + 
				((this.allocation == null)
				? ""
				: "[" + this.allocation.toString().replaceAll("\n", " ")+ "]");
	}
}
