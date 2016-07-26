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

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ABORTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ALLOCATED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REQUESTED;
import rst.timing.IntervalType.Interval;
import rst.timing.TimestampType.Timestamp;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class AllocatableResource implements SchedulerListener {

	private final static Logger LOG = Logger.getLogger(ExecutableResource.class.getName());
	private AllocationClient client;
	private ResourceAllocation allocation;
	private final BlockingQueue<State> queue = new LinkedBlockingQueue<>();
	private final boolean reschedule;

	public AllocatableResource(String description, Policy policy, Priority priority, Initiator initiator, String... resources) {
		this(description, policy, priority, initiator, false, resources);
	}

	public AllocatableResource(String description, Policy policy, Priority priority, Initiator initiator, boolean reschedule, String... resources) {
		this.reschedule = reschedule;
		this.allocation = ResourceAllocation.newBuilder().
				setId(UUID.randomUUID().toString().substring(0, 12)).
				setInitiator(initiator).
				setState(REQUESTED).
				setPolicy(policy).
				setPriority(priority).
				setDescription(description).
				addAllResourceIds(Arrays.asList(resources)).
				buildPartial();
	}

	public void schedule(long delay, long duration) throws RSBException {

		long now = System.currentTimeMillis();
		long start = now + delay;
		long end = start + duration;

		Interval.Builder interval = Interval.newBuilder().
				setBegin(Timestamp.newBuilder().setTime(start)).
				setEnd(Timestamp.newBuilder().setTime(end));

		this.allocation = ResourceAllocation.newBuilder(this.allocation).
				setSlot(interval).
				build();

		this.queue.add(this.allocation.getState());
		this.client = new AllocationClient(this.allocation);
		this.client.addSchedulerListener(this);
		this.client.schedule();
	}

	private void reschedule() throws RSBException {
		long now = System.currentTimeMillis();
		Interval.Builder interval = Interval.newBuilder(this.allocation.getSlot()).
				setBegin(Timestamp.newBuilder().setTime(now));
		this.allocation = ResourceAllocation.newBuilder(this.allocation).
				setId(UUID.randomUUID().toString().substring(0, 12)).
				setState(REQUESTED).
				setSlot(interval).
				setPolicy(MAXIMUM).
				setInitiator(SYSTEM).
				build();

		this.queue.add(this.allocation.getState());
		this.client = new AllocationClient(this.allocation);
		this.client.addSchedulerListener(this);
		this.client.schedule();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ((this.allocation.getDescription() == null) ? "" : "[" + this.allocation.getDescription() + "]");
	}

	@Override
	public void allocationUpdated(ResourceAllocation allocation, String cause) {
		this.queue.add(allocation.getState());
		if (this.reschedule && allocation.getState().equals(ABORTED)) {
			try {
				reschedule();
			} catch (RSBException e) {
				LOG.log(Level.WARNING, "Rescheduling failed", e);
			}
		}
	}

	public State getState() {
		return this.queue.peek();
	}

	public void await(State state) throws InterruptedException {
		while (!this.queue.contains(state)) {
			Thread.sleep(100);
		}
	}

	public void await(State state, long timeout) throws InterruptedException, TimeoutException {
		long start = System.currentTimeMillis();
		while (!this.queue.contains(state)) {
			if (start + timeout < System.currentTimeMillis()) {
				throw new TimeoutException();
			}
			Thread.sleep(100);
		}
	}

	public void shutdown() throws RSBException {
		switch (getState()) {
			case REQUESTED:
			case SCHEDULED:
				client.cancel();
				break;
			case ALLOCATED:
				client.abort();
				break;
			default:
				LOG.log(Level.WARNING, "Shutdown called in inactive state");
				break;
		}
	}
}
