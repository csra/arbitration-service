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
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class TimableSleepingSkill extends Skill {

	private long time;
	private long slice;
	
	public TimableSleepingSkill(String name, String resource, long time, Policy policy, Priority priority) throws InitializeException {
		super(name, resource, policy, priority);
		this.time = time;
	}

	@Override
	public void execute(long slice) throws InterruptedException {
		this.slice = slice;
		
		long start = System.currentTimeMillis();
		System.out.println(this + " execute, time: " + this.time  + ", slice: " + this.slice);
		Thread.sleep(Math.max(0, this.time));
		long stop = System.currentTimeMillis();
		System.out.println(this + " slept: " + (stop - start) + "ms.");
	}

	public long getTime() {
		return this.time;
	}

	public long getSlice() {
		return this.slice;
	}
}
