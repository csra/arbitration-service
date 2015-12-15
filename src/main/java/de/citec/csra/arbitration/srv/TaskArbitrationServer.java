/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.arbitration.srv;

import com.google.protobuf.ByteString;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Event;
import rsb.Factory;
import rsb.Handler;
import rsb.Informer;
import rsb.InitializeException;
import rsb.Listener;
import rsb.RSBException;
import rsb.converter.ConversionException;
import rsb.converter.ProtocolBufferConverter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.TaskStateType.TaskState;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.HANDLER;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.SUBMITTER;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ACCEPTED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.REJECTED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class TaskArbitrationServer implements Handler {

	private final static Logger LOG = Logger.getLogger(TaskArbitrationServer.class.getName());
	private final Informer informer;
	private final Listener listener;
	private final ProtocolBufferConverter<ResourceAllocation> conv;
	private final ByteString schema;
	private final DefaultAllocationMap map;
	private final ResourceAllocationService alloc;

	public TaskState updateTask(TaskState t, boolean success) {
		TaskState.State state = success ? ACCEPTED : REJECTED;
		return TaskState.newBuilder().
				setOrigin(HANDLER).
				setState(state).
				setSerial(t.getSerial() + 1).
				setWireSchema(t.getWireSchema()).
				setPayload(t.getPayload()).
				build();
	}

	public void sendTask(TaskState t) throws RSBException {
		this.informer.send(t);
	}

	@Override
	public void internalNotify(Event event) {

		if (event.getData() instanceof TaskState) {
			TaskState t = (TaskState) event.getData();

//			only regard foreign task states
			if (t.getOrigin().equals(SUBMITTER)) {

				ResourceAllocation needed = null;
//				contains resource allocation -> extract
				if (t.getWireSchema().equals(this.schema)) {
					try {
						needed = (ResourceAllocation) this.conv.deserialize(this.schema.toStringUtf8(), t.getPayload().asReadOnlyByteBuffer()).getData();
					} catch (ConversionException ex) {
						LOG.log(Level.SEVERE, "Could not deserialize resource allocation type", ex);
						return;
					}
//				contains only task description -> infer resources with heuristic (default allocation map)
				} else {
					needed = map.getRequiredResources(t, event);
				}

				if (needed != null) {
					boolean success = alloc.allocate(needed);
					TaskState update = updateTask(t, success);
					try {
						sendTask(update);
					} catch (RSBException ex) {
						LOG.log(Level.SEVERE, "Could not update task description", ex);
					}
				} else {
					LOG.log(Level.WARNING, "Resource requirement for task ''{0}'' unknown, disregarding.", t);
				}

			}

		} else {
			LOG.log(Level.WARNING, "Unsupported data type ''{0}''", event.getData().getClass());
		}
	}

	public TaskArbitrationServer(String scope) throws InitializeException {
		this.listener = Factory.getInstance().createListener(scope);
		this.informer = Factory.getInstance().createInformer(scope);
		this.conv = new ProtocolBufferConverter<>(ResourceAllocation.getDefaultInstance());
		this.schema = ByteString.copyFromUtf8(this.conv.getSignature().getSchema());

		this.map = DefaultAllocationMap.getInstance();
		this.alloc = ResourceAllocationService.getInstance();
	}

	public void activate() throws RSBException, InterruptedException {
		listener.addHandler(this, true);
		listener.activate();
		informer.activate();
	}

	public void waitForShutdown() {
		while (true) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException ex) {
				LOG.log(Level.SEVERE, "Waiting interrupted", ex);
			}
		}
	}

	public void deactivate() throws RSBException, InterruptedException {
		listener.removeHandler(this, true);
		listener.deactivate();
		informer.deactivate();
	}

}
