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

import de.citec.csra.allocation.srv.AllocationService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Factory;
import rsb.Informer;
import rsb.InitializeException;
import rsb.Listener;
import rsb.RSBException;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rsb.filter.OriginFilter;
import rsb.util.QueueAdapter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ABORTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.CANCELLED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.RELEASED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REQUESTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.SCHEDULED;
import rst.timing.IntervalType;
import rst.timing.IntervalType.Interval;
import rst.timing.TimestampType;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public abstract class Skill implements SchedulerController, Runnable {

	static {
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(ResourceAllocation.getDefaultInstance()));
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(Interval.getDefaultInstance()));
	}

	private final static Logger LOG = Logger.getLogger(Skill.class.getName());
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private Future<?> f;

	private final String description;
	private final String resources;
	private final Policy policy;
	private final Priority priority;
	private final Informer informer;
	private final Listener listener;
	private ResourceAllocation allocation;
	private final QueueAdapter qa = new QueueAdapter();
	private final BlockingQueue<ResourceAllocation> queue = qa.getQueue();
	private final Set<SchedulerListener> listeners = new HashSet<>();

	public Skill(String description, String resource, Policy policy, Priority priority) throws InitializeException {
		this.description = description;
		this.resources = resource;
		this.policy = policy;
		this.priority = priority;
		this.informer = Factory.getInstance().createInformer(AllocationService.SCOPE);
		this.listener = Factory.getInstance().createListener(AllocationService.SCOPE);
	}

	public void addSchedulerListener(SchedulerListener l) {
		this.listeners.add(l);
	}

	public void activate() throws RSBException, InterruptedException {
		this.informer.activate();
		this.listener.addFilter(new OriginFilter(this.informer.getId(), true));
		this.listener.addHandler(this.qa, true);
		this.listener.activate();
		new Thread(() -> {
			while (listener.isActive()) {
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
				}
			}
		}).start();
	}

	public void schedule(long delay, long duration) throws InterruptedException, RSBException {

		long now = System.currentTimeMillis();
		long start = now + delay;
		long end = start + duration;

		IntervalType.Interval.Builder interval = IntervalType.Interval.newBuilder().
				setBegin(TimestampType.Timestamp.newBuilder().setTime(start)).
				setEnd(TimestampType.Timestamp.newBuilder().setTime(end));

		this.allocation = ResourceAllocation.newBuilder().
				setId(UUID.randomUUID().toString().substring(0, 12)).
				setInitiator(SYSTEM).
				setState(REQUESTED).
				setSlot(interval).
				setPolicy(policy).
				setPriority(priority).
				setDescription(description).
				addResourceIds(resources).
				build();

		LOG.log(Level.FINE, "resource allocation scheduled by client: ''{0}''", allocation);
		synchronized (informer) {
			if (this.informer.isActive()) {
				this.informer.publish(allocation);
			}
		}
	}

	private boolean getFuture() throws InterruptedException {
		if (f == null || f.isCancelled()) {
			return false;
		} else {
			try {
				f.get();
			} catch (ExecutionException ex) {
				LOG.log(Level.SEVERE, "Execution failed", ex);
				return false;
			}
			return f.isDone();
		}

	}

	public boolean await() throws InterruptedException {
		while (isAlive()) {
			Thread.sleep(100);
		}
		return getFuture();
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
	public void abort() throws RSBException {
		if (isAlive()) {
			this.allocation = ResourceAllocation.newBuilder(this.allocation).setState(ABORTED).build();
			LOG.log(Level.FINE, "resource allocation aborted by client: ''{0}''", allocation);
			synchronized (informer) {
				if (this.informer.isActive()) {
					this.informer.publish(this.allocation);
				}
			}
		} else {
			LOG.log(Level.FINE, "resource allocation not active anymore ({0}), client aborting skipped: ''{1}''", new Object[]{allocation.getState(), allocation});
		}
	}

	@Override
	public void release() throws RSBException {
		if (isAlive()) {
			this.allocation = ResourceAllocation.newBuilder(this.allocation).setState(RELEASED).build();
			LOG.log(Level.FINE, "resource allocation released by client: ''{0}''", allocation);
			synchronized (informer) {
				if (this.informer.isActive()) {
					this.informer.publish(this.allocation);
				}
			}
		} else {
			LOG.log(Level.FINE, "resource allocation not active anymore ({0}), client releasing skipped: ''{1}''", new Object[]{allocation.getState(), allocation});
		}
	}

	@Override
	public void cancel() throws RSBException {
		if (isAlive()) {
			this.allocation = ResourceAllocation.newBuilder(this.allocation).setState(CANCELLED).build();
			LOG.log(Level.FINE, "resource allocation cancelled by client: ''{0}''", allocation);
			synchronized (informer) {
				if (this.informer.isActive()) {
					this.informer.publish(this.allocation);
				}
			}
		} else {
			LOG.log(Level.FINE, "resource allocation not active anymore ({0}), client cancelling skipped: ''{1}''", new Object[]{allocation.getState(), allocation});
		}
	}

	private void remoteScheduled(ResourceAllocation allocation) {
		this.allocation = allocation;
		LOG.log(Level.FINE, "resource allocation scheduled by server: ''{0}''", allocation);
		for (SchedulerListener l : this.listeners) {
			l.scheduled(allocation);
		}
	}

	private void remoteRejected(ResourceAllocation allocation, String cause) {
		this.allocation = allocation;
		LOG.log(Level.FINE, "resource allocation rejected by server, shutting down: ''{0}''", allocation);
		for (SchedulerListener l : this.listeners) {
			l.rejected(allocation, cause);
		}
		shutdown();
	}

	private void remoteAllocated(ResourceAllocation allocation) {
		this.allocation = allocation;
		if (this.f == null) {
			LOG.log(Level.FINE, "resource allocation granted by server, starting execution: ''{0}''", allocation);
			for (SchedulerListener l : this.listeners) {
				l.allocated(allocation);
			}
			this.f = executor.submit(this);
		} else {
			LOG.log(Level.FINE, "resource allocation altered by server, starting execution: ''{0}''", allocation);
			for (SchedulerListener l : this.listeners) {
				l.allocated(allocation);
			}
		}
	}

	private void remoteAborted(ResourceAllocation allocation, String cause) {
		this.allocation = allocation;
		LOG.log(Level.FINE, "resource allocation aborted by server, shutting down: ''{0}''", allocation);
		for (SchedulerListener l : this.listeners) {
			l.aborted(allocation, cause);
		}
		shutdown();
	}

	
	private void remoteCancelled(ResourceAllocation allocation, String cause) {
		this.allocation = allocation;
		LOG.log(Level.FINE, "resource allocation cancelled by server, shutting down: ''{0}''", allocation);
		for (SchedulerListener l : this.listeners) {
			l.cancelled(allocation, cause);
		}
		shutdown();
	}

	private void remoteReleased(ResourceAllocation allocation) {
		this.allocation = allocation;
		LOG.log(Level.FINE, "resource allocation released by server, shutting down: ''{0}''", allocation);
		for (SchedulerListener l : this.listeners) {
			l.released(allocation);
		}
		shutdown();
	}

	private void shutdown() {
		if (f != null && !f.isDone()) {
			f.cancel(true);
		}
		executor.shutdown();
		try {
			executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
			if (listener.isActive()) {
				listener.deactivate();
			}
			synchronized (informer) {
				if (informer.isActive()) {
					informer.deactivate();

				}
			}
		} catch (RSBException | InterruptedException x) {
			Logger.getLogger(Skill.class
					.getName()).log(Level.SEVERE, null, x);
		}
	}

	public abstract void execute(long slice) throws Exception;

	@Override
	public void run() {
		long start = allocation.getSlot().getBegin().getTime();
		long end = allocation.getSlot().getEnd().getTime();
		long now = System.currentTimeMillis();

		if (start > now) {
			LOG.log(Level.WARNING, "permission to run in the future, starting anyways.");
		}
		long slice = end - now;
		if (slice > 0) {
			try {
				execute(slice);
			} catch (InterruptedException ex) {
				LOG.log(Level.SEVERE, allocation.getId() + ": execution interrupted", ex);
			} catch (Exception ex) {
				LOG.log(Level.SEVERE, allocation.getId() + ": execution failed", ex);
				try {
					abort();
				} catch (RSBException ex1) {
					LOG.log(Level.SEVERE, allocation.getId() + ": could not abort", ex1);
				}
			}
			try {
				release();
			} catch (RSBException ex) {
				LOG.log(Level.SEVERE, allocation.getId() + ": could not release", ex);
			}
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ((this.description == null) ? "" : "[" + this.description + "]");
	}
}
