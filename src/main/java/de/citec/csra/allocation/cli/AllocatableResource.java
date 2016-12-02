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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
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
	private final ResourceAllocation.Builder builder;
	private final LinkedBlockingDeque<State> queue = new LinkedBlockingDeque<>();

	public AllocatableResource(ResourceAllocation allocation) {
		this.builder = ResourceAllocation.newBuilder(allocation);
	}

	public AllocatableResource(String description, Policy policy, Priority priority, Initiator initiator, long delay, long duration, String... resources) {
		this(description, policy, priority, initiator, resources);
		this.builder.setSlot(buildRelative(delay, duration));
	}

	public AllocatableResource(String description, Policy policy, Priority priority, Initiator initiator, String... resources) {
		this.builder = ResourceAllocation.newBuilder().
				setId(UUID.randomUUID().toString().substring(0, 12)).
				setInitiator(initiator).
				setState(REQUESTED).
				setPolicy(policy).
				setPriority(priority).
				setDescription(description).
				addAllResourceIds(Arrays.asList(resources));
	}

	private Interval buildRelative(long delay, long duration) {
		long now = System.currentTimeMillis();
		long start = now + delay;
		long end = start + duration;
		return build(start, end);
	}

	private Interval build(long begin, long end) {
		return Interval.newBuilder().
				setBegin(Timestamp.newBuilder().setTime(begin)).
				setEnd(Timestamp.newBuilder().setTime(end)).build();
	}

	private void updateSlot(Interval interval) throws RSBException {
		this.builder.setSlot(interval);
		if (!this.queue.isEmpty()) {
			this.client.updateSlot(interval);
		}
	}

	public void shiftTo(long timestamp) throws RSBException {
		long newBegin = timestamp;
		long newEnd = newBegin + this.builder.getSlot().getEnd().getTime() - this.builder.getSlot().getBegin().getTime();
		updateSlot(build(newBegin, newEnd));
	}

	public void shift(long amount) throws RSBException {
		long newEnd = this.builder.getSlot().getEnd().getTime() + amount;
		long newBegin = this.builder.getSlot().getBegin().getTime() + amount;
		updateSlot(build(newBegin, newEnd));
	}

	public void extend(long duration) throws RSBException {
		long newEnd = this.builder.getSlot().getEnd().getTime() + duration;
		Interval newInterval = Interval.newBuilder(this.builder.getSlot()).setEnd(Timestamp.newBuilder().setTime(newEnd)).build();
		updateSlot(newInterval);
	}

//	private void reschedule() throws RSBException {
//		long now = System.currentTimeMillis();
//		Interval.Builder interval = Interval.newBuilder(this.allocation.getSlot()).
//				setBegin(Timestamp.newBuilder().setTime(now));
//		this.allocation = ResourceAllocation.newBuilder(this.allocation).
//				setId(UUID.randomUUID().toString().substring(0, 12)).
//				setState(REQUESTED).
//				setSlot(interval).
//				setPolicy(MAXIMUM).
//				setInitiator(SYSTEM).
//				build();
//
//		this.queue.add(this.allocation.getState());
//		this.client = new AllocationClient(this.allocation);
//		this.client.addSchedulerListener(this);
//		this.client.schedule();
//	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ((this.builder.getDescription() == null) ? "" : "[" + this.builder.getDescription() + "]");
	}
	
	public void addSchedulerListener(SchedulerListener l){
		this.client.addSchedulerListener(l);
	}
	
	public void removeSchedulerListener(SchedulerListener l){
		this.client.removeSchedulerListener(l);
	}

	@Override
	public void allocationUpdated(ResourceAllocation allocation, String cause) {
		this.queue.add(allocation.getState());
		this.builder.mergeFrom(allocation);
//		if (this.reschedule && allocation.getState().equals(ABORTED)) {
//			try {
//				reschedule();
//			} catch (RSBException e) {
//				LOG.log(Level.WARNING, "Rescheduling failed", e);
//			}
//		}
	}

	public State getState() {
		return this.queue.peekLast();
	}

	public void await(State state) throws InterruptedException {
		while (!this.queue.contains(state)) {
			Thread.sleep(50);
		}
	}

	public void await(State state, long timeout) throws InterruptedException, TimeoutException {
		long start = System.currentTimeMillis();
		while (!this.queue.contains(state)) {
			if (start + timeout < System.currentTimeMillis()) {
				throw new TimeoutException();
			}
			Thread.sleep(50);
		}
	}

	public void startup() throws RSBException {
		if (this.queue.isEmpty()) {
			this.queue.add(this.builder.getState());
			this.client = new AllocationClient(this.builder.build());
			this.client.addSchedulerListener(this);
			this.client.schedule();
		} else {
			LOG.log(Level.WARNING, "Startup called while already active ({0}), ignoring.", getState());
		}
	}

	public void shutdown() throws RSBException {
		switch (getState()) {
			case REQUESTED:
			case SCHEDULED:
				client.cancel();
				this.client.removeSchedulerListener(this);
				break;
			case ALLOCATED:
				client.abort();
				this.client.removeSchedulerListener(this);
				break;
			default:
				LOG.log(Level.WARNING, "Shutdown called in inactive state ({0}), ignoring.", getState());
				break;
		}
	}
}
