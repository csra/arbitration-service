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

import de.citec.csra.allocation.IntervalUtils;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ABORTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.CANCELLED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REQUESTED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public abstract class ExecutableResource<T> implements SchedulerListener, Adjustable, Executable, Callable<T> {

	private final static Logger LOG = Logger.getLogger(ExecutableResource.class.getName());
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private Future<T> result;
	private RemoteAllocation remote;
	private final ResourceAllocation.Builder builder;
	private ResourceAllocation allocation;

	public ExecutableResource(ResourceAllocation allocation) {
		this.builder = ResourceAllocation.newBuilder(allocation);
	}

	public ExecutableResource(String description, Policy policy, Priority priority, Initiator initiator, long delay, long duration, String... resources) {
		this.builder = ResourceAllocation.newBuilder().
				setInitiator(initiator).
				setPolicy(policy).
				setPriority(priority).
				setDescription(description).
				setSlot(IntervalUtils.buildRelativeRst(delay, duration)).
				addAllResourceIds(Arrays.asList(resources));
	}

	private void terminateExecution(boolean interrupt) {
		if (result != null && !result.isDone()) {
			result.cancel(interrupt);
		}
		try {
			remote.removeSchedulerListener(this);
			executor.shutdown();
			executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException x) {
			LOG.log(Level.SEVERE, "Interrupted during executor shutdown", x);
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void startup() throws RSBException {
		if (!this.builder.hasId()) {
			this.builder.setId(UUID.randomUUID().toString().substring(0, 12));
		}
		if (this.builder.hasState()) {
			LOG.log(Level.WARNING, "Invalid initial state ''{0}'', altering to ''{1}''.", new Object[]{this.builder.getState(), REQUESTED});
		}
		this.builder.setState(REQUESTED);
		this.result = executor.submit(this);
		this.allocation = this.builder.build();
		this.remote = new RemoteAllocation(this.allocation);
		this.remote.addSchedulerListener(this);
		this.remote.schedule();
	}

	@Override
	public void shutdown() throws RSBException {
		switch (allocation.getState()) {
			case REQUESTED:
			case SCHEDULED:
				remote.cancel();
				allocation = ResourceAllocation.newBuilder(allocation).setState(CANCELLED).build();
				terminateExecution(false);
				break;
			case ALLOCATED:
				remote.abort();
				allocation = ResourceAllocation.newBuilder(allocation).setState(ABORTED).build();
				terminateExecution(true);
				break;
			default:
				LOG.log(Level.WARNING, "Shutdown called in inactive state");
				terminateExecution(false);
				break;
		}
	}

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
				Thread.sleep(10);
			} catch (InterruptedException ex) {
				LOG.log(Level.SEVERE, "Startup interrupted in state " + this.allocation.getState(), ex);
				Thread.interrupted();
				return null;
			}
		}

		long start = this.allocation.getSlot().getBegin().getTime();
		long now = System.currentTimeMillis();

		if (start > now) {
			LOG.log(Level.WARNING, "permission to run in the future, starting anyways.");
		}

		T res = null;
		try {
			LOG.log(Level.FINE, "Starting user code execution for {0}ms.", remaining());
			res = execute();
			LOG.log(Level.FINE, "User code execution returned with ''{0}''", res);
			try {
				this.remote.release();
			} catch (RSBException ex) {
				LOG.log(Level.WARNING, "Could not release resources at server", ex);
			}
		} catch (ExecutionException ex) {
			LOG.log(Level.WARNING, "User code execution failed, aborting allocation at server", ex);
			try {
				this.remote.abort();
			} catch (RSBException ex1) {
				LOG.log(Level.WARNING, "Could not abort resource allocation at server", ex1);
			}
		} catch (InterruptedException ex) {
			LOG.log(Level.FINER, "User code interrupted, aborting allocation at server");
			try {
				this.remote.abort();
			} catch (RSBException ex1) {
				LOG.log(Level.WARNING, "Could not abort resource allocation at server", ex1);
			}
		}
		return res;

	}

	public Future<T> getFuture() {
		return this.result;
	}

	public long remaining() {
		switch (this.allocation.getState()) {
			case REQUESTED:
			case SCHEDULED:
			case ALLOCATED:
				return Math.max(0, allocation.getSlot().getEnd().getTime() - System.currentTimeMillis() - 100);
			case ABORTED:
			case CANCELLED:
			case REJECTED:
			case RELEASED:
			default:
				return -1;
		}
	}

	@Override
	public void shift(long amount) throws RSBException {
		this.remote.shift(amount);
	}

	@Override
	public void shiftTo(long timestamp) throws RSBException {
		this.remote.shiftTo(timestamp);
	}

	@Override
	public void extend(long amount) throws RSBException {
		this.remote.extend(amount);
	}
	
	@Override
	public void extendTo(long timestamp) throws RSBException {
		this.remote.extendTo(timestamp);
	}
	
	@Override
	public void allocationUpdated(ResourceAllocation allocation) {
		this.allocation = allocation;
		switch (allocation.getState()) {
			case SCHEDULED:
				break;
			case ALLOCATED:
				updated(allocation);
				break;
			case REJECTED:
			case CANCELLED:
				terminateExecution(false);
				break;
			case ABORTED:
			case RELEASED:
				terminateExecution(true);
				break;
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + 
				((this.allocation == null) ? 
				"[" + this.builder.buildPartial().toString().replaceAll("\n", " ") + "]" : 
				"[" + this.allocation.toString().replaceAll("\n", " ") + "]");
	}

	public abstract T execute() throws ExecutionException, InterruptedException;

	public abstract boolean updated(ResourceAllocation allocation);

}
