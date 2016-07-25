/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.allocation;

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
	private ResourceAllocation allocation;
	private final BlockingQueue<State> queue = new LinkedBlockingQueue<>();

	public AllocatableResource(String description, Policy policy, Priority priority, Initiator initiator, String... resources) {
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

	@Override
	public String toString() {
		return getClass().getSimpleName() + ((this.allocation.getDescription() == null) ? "" : "[" + this.allocation.getDescription() + "]");
	}

	@Override
	public void allocationUpdated(ResourceAllocation allocation, String cause) {
		this.queue.add(allocation.getState());
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
			if(start + timeout < System.currentTimeMillis()){
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
