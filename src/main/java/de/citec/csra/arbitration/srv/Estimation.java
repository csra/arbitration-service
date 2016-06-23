/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.arbitration.srv;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import rst.communicationpatterns.ResourceAllocationType;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.PRESERVE;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.NORMAL;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REQUESTED;
import rst.timing.IntervalType;
import rst.timing.TimestampType;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class Estimation {

	private final static long ADD = 2000;

	private final String submitter;
	private ResourceAllocation.Builder builder;

	private Set<String> resources;
	private String handler;

	private int count;
	private long duration;

	public Estimation(String submitter) {
		this.count = 1;
		this.submitter = submitter;
		initBuilder();
		initHandler();
		initDuration();
		initResources();
	}

	private void initBuilder() {
		this.builder = ResourceAllocationType.ResourceAllocation.newBuilder().setId(UUID.randomUUID().toString().substring(0, 12)).
				setState(REQUESTED).
				setPolicy(PRESERVE).
				setInitiator(SYSTEM).
				setPriority(NORMAL);
	}


	private void initHandler() {
		String stored = DefaultAllocationMap.getInstance().getHandler(this.submitter);
		if (stored != null) {
			this.handler = stored;
		} else {
			setHandler(this.submitter.replaceAll(ArbitrationServer.getScope(), ""));
		}
	}

	private void initDuration() {
		Long stored = DefaultAllocationMap.getInstance().getDuration(this.submitter);
		if (stored != null) {
			this.duration = stored;
		} else {
			addDuration(10000);
		}
	}
	
	private void initResources() {
		Set<String> stored = DefaultAllocationMap.getInstance().getResources(this.submitter);
		if (stored != null) {
			System.out.println(stored);
			this.resources = stored;
		} else {
			addResource(this.handler);
		}
	}

	public String getHandler() {
		return handler;
	}

	public ResourceAllocation getResources() {
		builder.setDescription(this.handler);
		setDuration(builder);
		setResources(builder);
		return builder.build();
	}

	private void setDuration(ResourceAllocationType.ResourceAllocation.Builder builder) {
		long now = System.currentTimeMillis();
		builder.setSlot(IntervalType.Interval.newBuilder().
				setBegin(TimestampType.Timestamp.newBuilder().setTime(now)).
				setEnd(TimestampType.Timestamp.newBuilder().setTime(now + this.duration + ADD)));
	}

	private void setResources(ResourceAllocationType.ResourceAllocation.Builder builder) {
		builder.addAllResourceIds(this.resources);
	}

	void addDuration(long duration) {
//		this.duration = (this.duration * this.count + duration) / ++count;
		this.duration = Math.max(this.duration, duration);
		DefaultAllocationMap.getInstance().setDuration(this.submitter, this.duration);
	}

	void addResource(String resource) {
		if(this.resources == null){
			this.resources = new HashSet<>();
		}
		this.resources.add(resource);
		DefaultAllocationMap.getInstance().setResources(this.submitter, this.resources);
	}

	void setHandler(String handler) {
		this.handler = handler;
		DefaultAllocationMap.getInstance().setHandler(this.submitter, this.handler);
	}

}
