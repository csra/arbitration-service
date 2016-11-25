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
	private String scxml;

	private int count;
	private long duration;

	public Estimation(String submitter) {
		this.count = 1;
		this.submitter = submitter;
		initBuilder();
		initHandler();
		initSCXML();
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
	
	private void initSCXML() {
		String stored = DefaultAllocationMap.getInstance().getSCXML(this.submitter);
		if (stored != null) {
			this.scxml = stored;
		} else {
			setHandler(this.submitter.
					replaceAll("coordination", "").
					replaceAll("scenario", "").
					replaceAll("/", ""));
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
	
	public String getSCXML() {
		return scxml;
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
	
	void setSCXML(String scxml) {
		this.scxml = scxml;
		DefaultAllocationMap.getInstance().setHandler(this.submitter, this.scxml);
	}
}
