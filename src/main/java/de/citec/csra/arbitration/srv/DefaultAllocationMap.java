/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.arbitration.srv;

import rsb.Event;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.APPEND;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.NORMAL;
import rst.communicationpatterns.TaskStateType.TaskState;
import rst.timing.IntervalType;
import rst.timing.TimestampType;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class DefaultAllocationMap {
	private DefaultAllocationMap() {}
	private static DefaultAllocationMap instance;
	public static DefaultAllocationMap getInstance(){
		if(instance == null){
			instance = new DefaultAllocationMap();
		}
		return instance;
	}
	
	public ResourceAllocation getRequiredResources(TaskState ts, Event e) {
		long now = System.currentTimeMillis();
		ResourceAllocation a = ResourceAllocation.newBuilder().
				setDescription("example").
				setTimeframe(IntervalType.Interval.newBuilder().
						setBegin(TimestampType.Timestamp.newBuilder().setTime(now)).
						setEnd(TimestampType.Timestamp.newBuilder().setTime(now + 1000))).
				setInitiator(SYSTEM).
				setPriority(NORMAL).
				setPolicy(APPEND).
				addResourceIds("Kitchen").
				build();
		return a;
	}
}
