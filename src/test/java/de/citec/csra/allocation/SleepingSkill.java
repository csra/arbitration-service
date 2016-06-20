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

import rsb.InitializeException;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ABORTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ALLOCATED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.CANCELLED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REJECTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.RELEASED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.SCHEDULED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class SleepingSkill extends Skill implements SchedulerListener {

	private long duration;
	private int scheduled;
	private int rejected;
	private int allocated;
	private int cancelled;
	private int aborted;
	private int released;

	public SleepingSkill(String name, String resource, Policy policy, Priority priority) throws InitializeException {
		super(name, resource, policy, priority);

	}
	
	
	@Override
	public void schedule() throws RSBException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void schedule(long delay, long duration) throws InterruptedException, RSBException {
		this.duration = duration;
		this.addSchedulerListener(this);
		super.schedule(delay, duration);
	}

	@Override
	public void execute(long slice) throws InterruptedException {
		long start = System.currentTimeMillis();
		System.out.println(this + " execute, slice: " + slice);
		Thread.sleep(Math.max(0, slice - 100));
		System.out.println(this + " slept: " + (System.currentTimeMillis() - start) + "ms.");
	}

	public long getDuration() {
		return duration;
	}

	@Override
	public void scheduled(ResourceAllocationType.ResourceAllocation allocation) {
		if (allocation != null) {
			this.duration = allocation.getSlot().getEnd().getTime() - allocation.getSlot().getBegin().getTime();
			if(allocation.getState().equals(SCHEDULED)){
				this.scheduled++;
			}
		} else {
			this.duration = 0;
		}
	}

	@Override
	public void rejected(ResourceAllocationType.ResourceAllocation allocation, String cause) {
		if (allocation != null) {
			this.duration = allocation.getSlot().getEnd().getTime() - allocation.getSlot().getBegin().getTime();
			if(allocation.getState().equals(REJECTED)){
				this.rejected++;
			}
		} else {
			this.duration = 0;
		}
	}

	@Override
	public void allocated(ResourceAllocationType.ResourceAllocation allocation) {
		if (allocation != null) {
			this.duration = allocation.getSlot().getEnd().getTime() - allocation.getSlot().getBegin().getTime();
			if(allocation.getState().equals(ALLOCATED)){
				this.allocated++;
			}
		} else {
			this.duration = 0;
		}
	}

	@Override
	public void cancelled(ResourceAllocationType.ResourceAllocation allocation, String cause) {
		if (allocation != null) {
			this.duration = allocation.getSlot().getEnd().getTime() - allocation.getSlot().getBegin().getTime();
			if(allocation.getState().equals(CANCELLED)){
				this.cancelled++;
			}
		} else {
			this.duration = 0;
		}
	}

	@Override
	public void aborted(ResourceAllocationType.ResourceAllocation allocation, String cause) {
		if (allocation != null) {
			this.duration = allocation.getSlot().getEnd().getTime() - allocation.getSlot().getBegin().getTime();
			if(allocation.getState().equals(ABORTED)){
				this.aborted++;
			}
		} else {
			this.duration = 0;
		}
	}

	@Override
	public void released(ResourceAllocationType.ResourceAllocation allocation) {
		if (allocation != null) {
			this.duration = allocation.getSlot().getEnd().getTime() - allocation.getSlot().getBegin().getTime();
			if(allocation.getState().equals(RELEASED)){
				this.released++;
			}
		} else {
			this.duration = 0;
		}
	}

	public int getScheduled() {
		return scheduled;
	}

	public int getRejected() {
		return rejected;
	}

	public int getAllocated() {
		return allocated;
	}

	public int getCancelled() {
		return cancelled;
	}

	public int getAborted() {
		return aborted;
	}

	public int getReleased() {
		return released;
	}

}
