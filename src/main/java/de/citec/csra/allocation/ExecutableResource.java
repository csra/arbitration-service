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

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.InitializeException;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REQUESTED;
import rst.timing.IntervalType;
import rst.timing.TimestampType;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public abstract class ExecutableResource<T> implements SchedulerListener, Callable<T> {

	private final static Logger LOG = Logger.getLogger(ExecutableResource.class.getName());
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private Future<T> result;

	private final String description;
	private final String resources;
	private final Policy policy;
	private final Priority priority;
	private AllocationClient client;
	private ResourceAllocation allocation;

	public ExecutableResource(String description, String resource, Policy policy, Priority priority) throws InitializeException {
		this.description = description;
		this.resources = resource;
		this.policy = policy;
		this.priority = priority;
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

		this.result = executor.submit(this);
		this.client = new AllocationClient(this.allocation);
		this.client.addSchedulerListener(this);
		this.client.schedule();
	}

	public Future<T> getFuture() {
		return this.result;
	}

//	public T getResult() throws InterruptedException {
//		while (this.client.isAlive() && !Thread.interrupted()) {
//			Thread.sleep(100);
//		}
//		if (result == null || result.isCancelled()) {
//			return null;
//		} else {
//			try {
//				return result.get();
//			} catch (ExecutionException ex) {
//				LOG.log(Level.SEVERE, "Execution failed", ex);
//				return null;
//			}
////			return result.isDone();
//		}
//	}
	public void shutdown(boolean interrupt) {
		if (result != null && !result.isDone()) {
			result.cancel(interrupt);
		}
		try {
			client.removeSchedulerListener(this);
			executor.shutdown();
			executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException x) {
			LOG.log(Level.SEVERE, "Interrupted during executor shutdown", x);
			Thread.currentThread().interrupt();
		}
	}

	public abstract T execute(long slice) throws ExecutionException;

	@Override
	public T call() {
		awaitStart:
		while (!Thread.interrupted()) {
			switch (this.allocation.getState()) {
				case REQUESTED:
				case SCHEDULED:
					break;
				case ALLOCATED:
					break awaitStart;
				case ABORTED:
				case CANCELLED:
				case REJECTED:
				case RELEASED:
					return null;
			}
			try {
				Thread.sleep(50);
			} catch (InterruptedException ex) {
				LOG.log(Level.SEVERE, "Startup interrupted in state " + this.allocation.getState(), ex);
				Thread.interrupted();
				return null;
			}
		}

		long start = this.allocation.getSlot().getBegin().getTime();
		long end = this.allocation.getSlot().getEnd().getTime();
		long now = System.currentTimeMillis();

		if (start > now) {
			LOG.log(Level.WARNING, "permission to run in the future, starting anyways.");
		}

		T res = null;
		try {
			LOG.log(Level.FINE, "Starting user code execution for {0}ms.", (end - now));
			res = execute(end - now);
			LOG.log(Level.FINE, "User code execution returned with ''{0}''", res);
			try {
				this.client.release();
			} catch (RSBException ex) {
				LOG.log(Level.WARNING, "Could not release resources", ex);
			}
		} catch (ExecutionException ex) {
			LOG.log(Level.WARNING, "Use code execution failed", ex);
			try {
				this.client.abort();
			} catch (RSBException ex1) {
				LOG.log(Level.WARNING, "Could not abort resources", ex1);
			}
		}
		return res;

	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ((this.description == null) ? "" : "[" + this.description + "]");
	}

	@Override
	public void scheduled(ResourceAllocation allocation) {
		this.allocation = allocation;
	}

	@Override
	public void rejected(ResourceAllocation allocation, String cause) {
		this.allocation = allocation;
		shutdown(false);
	}

	@Override
	public void allocated(ResourceAllocation allocation) {
		this.allocation = allocation;
	}

	@Override
	public void cancelled(ResourceAllocation allocation, String cause) {
		this.allocation = allocation;
		shutdown(false);
	}

	@Override
	public void aborted(ResourceAllocation allocation, String cause) {
		this.allocation = allocation;
		shutdown(true);
	}

	@Override
	public void released(ResourceAllocation allocation) {
		this.allocation = allocation;
		shutdown(true);
	}

}
