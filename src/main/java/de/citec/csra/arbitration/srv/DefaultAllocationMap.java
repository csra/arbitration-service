/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.arbitration.srv;

import rsb.Event;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.NORMAL;
import rst.communicationpatterns.TaskStateType.TaskState;
import rst.timing.DurationType;

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
		ResourceAllocation a = ResourceAllocation.newBuilder().
				setDescription("example").
				setDuration(DurationType.Duration.newBuilder().setTime(1000)).
				setInitiator(SYSTEM).
				setPriority(NORMAL).
				addLocationIds("Kitchen").
				build();
		return a;
	}
}
