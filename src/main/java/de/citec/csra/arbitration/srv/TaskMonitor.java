/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.arbitration.srv;

import com.google.protobuf.ByteString;
import de.citec.csra.allocation.AllocationClient;
import de.citec.csra.allocation.SchedulerListener;
import de.citec.csra.arbitration.task.cli.TaskReceiver;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Event;
import rsb.EventId;
import rsb.Factory;
import rsb.Informer;
import rsb.InitializeException;
import rsb.RSBException;
import rsb.Scope;
import rsb.converter.ConversionException;
import rsb.converter.ProtocolBufferConverter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.TaskStateType.TaskState;
import rst.communicationpatterns.TaskStateType.TaskState.Origin;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.HANDLER;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.SUBMITTER;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORTED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.REJECTED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class TaskMonitor implements SchedulerListener {

	private final static Logger LOG = Logger.getLogger(TaskMonitor.class.getName());

	private final Informer handler;
	private final Informer submitter;
	private final Scope submitterScope;
	private final Scope handlerScope;
	private final TaskReceiver handlerReceiver;
	private final TaskReceiver submitterReceiver;

	private final AllocationClient allocation;
	private final static ProtocolBufferConverter<ResourceAllocation> CONV = new ProtocolBufferConverter<>(ResourceAllocation.getDefaultInstance());

	private TaskState task;
	private long initiated = -1;
	private EventId submitterCause;
	private final Estimation estimation;

	public TaskMonitor(TaskState task, Scope submitterScope, EventId submitterCause, Informer submitter) throws InitializeException, InterruptedException, RSBException {

		this.task = task;
		this.submitterCause = submitterCause;

		this.submitter = submitter;
		this.submitterScope = submitterScope;

		ResourceAllocation needed = null;
		if (task.getWireSchema().equals(ByteString.copyFromUtf8(CONV.getSignature().getSchema()))) {
			try {
				needed = (ResourceAllocation) CONV.deserialize(CONV.getSignature().getSchema(), task.getPayload().asReadOnlyByteBuffer()).getData();
			} catch (ConversionException ex) {
				LOG.log(Level.SEVERE, "Could not deserialize resource allocation type", ex);
			}
		}
		
		this.estimation = new Estimation(submitterScope.toString());
		this.handlerScope = new Scope(this.estimation.getHandler());

//		no information about needed resources -> infer resources with heuristic (default allocation map)
		if (needed == null) {
			needed = this.estimation.getResources();
		}

		this.handler = Factory.getInstance().createInformer(this.handlerScope);

		this.submitterReceiver = new TaskReceiver(this.submitterScope);
		this.handlerReceiver = new TaskReceiver(this.handlerScope);

		this.submitterReceiver.ignore(this.submitter.getId());
		this.handlerReceiver.ignore(this.handler.getId());

		this.allocation = new AllocationClient(needed);
	}

	public void activate() {
		LOG.log(Level.INFO, "Activating task monitor for ''{0}''", this.handlerScope);
		try {
			handler.activate();
			handlerReceiver.activate();
			submitterReceiver.activate();
			allocation.addSchedulerListener(this);
			allocation.schedule();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void deactivate() {
		LOG.log(Level.INFO, "Deactivating task monitor for ''{0}''", this.handlerScope);
		try {
			handler.deactivate();
			handlerReceiver.deactivate();
			submitterReceiver.deactivate();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void publish(Origin... whereTo) {
		for (Origin w : whereTo) {
			EventId c = submitterCause;
			Informer i;
			Scope s;
			Origin o;

			switch (w) {
				case HANDLER:
					o = SUBMITTER;
					i = handler;
					s = handlerScope;
					break;
				case SUBMITTER:
					o = HANDLER;
					i = submitter;
					s = submitterScope;
					break;
				default:
					return;
			}
			try {
				TaskState toSend = TaskState.newBuilder(this.task).setOrigin(o).build();
				LOG.log(Level.INFO, "Updating task for ''{0}'' at ''{1}'' with ''{2}''", new String[]{w.name(), s.toString(), toSend.toString().replaceAll("\n", " ")});
				Event e = new Event(s, TaskState.class, toSend);
				e.addCause(c);
				i.publish(e);
			} catch (RSBException ex) {
				Logger.getLogger(TaskMonitor.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}
	
	@Override
	public void allocationUpdated(ResourceAllocation allocation, String cause) {
		switch(allocation.getState()){
			case SCHEDULED:
				LOG.log(Level.FINE, "Ignoring resource scheduling ''{0}''", allocation.toString().replaceAll("\n", " "));
				break;
			case REJECTED:
				updateTask(REJECTED, SUBMITTER);
				break;
			case ALLOCATED:
				allocated(allocation);
				break;
			case CANCELLED:
				updateTask(ABORTED, SUBMITTER, HANDLER);
				break;
			case ABORTED:
				updateTask(ABORTED, SUBMITTER, HANDLER);
				break;
			case RELEASED:
				updateTask(ABORTED, SUBMITTER, HANDLER);
				break;
		}
	}

	public void allocated(ResourceAllocation allocation) {

		if (initiated < 0) {
			LOG.log(Level.INFO, "Establishing task communication between ''{0}'' and ''{1}''", new Object[]{this.submitterScope, this.handlerScope});
			submitterReceiver.addTaskListener((TaskState t) -> {
//				System.out.println("received state from submitter, redirecting to handler: " + t.getState());
				updateTask(t, HANDLER);
			});

			handlerReceiver.addTaskListener(t -> {
//				System.out.println("received state from handler, redirecting to submitter: " + t.getState());
				switch (t.getState()) {
					case COMPLETED:
						if (initiated > 0) {
//							System.out.println("update duration: " + (System.currentTimeMillis() - initiated));
							estimation.addDuration(System.currentTimeMillis() - initiated);
						}
						break;
					default:
						break;
				}
				updateTask(t, SUBMITTER);
			});

			new Thread(handlerReceiver).start();
			new Thread(submitterReceiver).start();

			publish(HANDLER);
			initiated = System.currentTimeMillis();
		} else {
			LOG.log(Level.INFO, "Task communication between ''{0}'' and ''{1}'' already established, ignoring re-allocation", new Object[]{this.submitterScope, this.handlerScope});
		}
	}

	public void updateTask(TaskState.State state, Origin... whereTo) {

		int serial = 0;
		switch (state) {
			case ABORT:
				serial = 2;
				break;
			default:
			case INITIATED:
				serial = 0;
				break;
		}

		updateTask(TaskState.newBuilder(this.task).
				setState(state).
				setSerial(serial).
				build(), whereTo);
	}

	public void updateTask(TaskState task, Origin... whereTo) {
		this.task = task;
		publish(whereTo);
		switch (task.getState()) {
			default:
				break;
			case ABORT:
			case ABORTED:
			case FAILED:
			case REJECTED:
			case COMPLETED:
				deactivate();
				break;
		}
	}

}
