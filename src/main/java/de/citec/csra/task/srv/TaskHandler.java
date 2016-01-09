/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.task.srv;

import com.google.protobuf.ByteString;
import de.citec.csra.task.SerializationService;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Event;
import rsb.Factory;
import rsb.Handler;
import rsb.Informer;
import rsb.InitializeException;
import rsb.Listener;
import rsb.RSBException;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rst.communicationpatterns.TaskStateType.TaskState;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.HANDLER;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.SUBMITTER;
import rst.communicationpatterns.TaskStateType.TaskState.State;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORT;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORTED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.COMPLETED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.FAILED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.INITIATED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.REJECTED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public abstract class TaskHandler<T, V> implements Handler {

	static {
		DefaultConverterRepository.getDefaultConverterRepository().addConverter(new ProtocolBufferConverter<>(TaskState.getDefaultInstance()));
	}
	private final static Logger LOG = Logger.getLogger(TaskHandler.class.getName());
	private final Informer informer;
	private final Listener listener;
	private final Class<T> inCls;
	private final SerializationService<T> inSerial;
	private final SerializationService<V> outSerial;

	public TaskHandler(String scope, Class<T> cls, Class<V> res) throws InitializeException {
		this.informer = Factory.getInstance().createInformer(scope);
		this.listener = Factory.getInstance().createListener(scope);
		this.inCls = cls;
		this.inSerial = new SerializationService<>(cls);
		this.outSerial = new SerializationService<>(res);
	}

	public void activate() throws RSBException, InterruptedException {
		this.listener.addHandler(this, true);
		this.informer.activate();
		this.listener.activate();
		LOG.log(Level.FINE, "Activated listener/informer pair at ''{0}''", listener.getScope());
	}

	public void deactivate() throws InterruptedException, RSBException {
		this.listener.deactivate();
		this.informer.deactivate();
	}

	@Override
	public void internalNotify(Event event) {
		if (event.getData() instanceof TaskState) {
			TaskState t = (TaskState) event.getData();

//			only regard foreign task states that are initiated and not empty
			if (t.getOrigin().equals(SUBMITTER)) {
				T payload = inSerial.deserialize(t.getPayload());
				if (t.getState().equals(INITIATED)) {

					State init = initializeTask(payload);
					updateTask(t, init);
					if (init != REJECTED) {
						try {
							V result = handleTask(payload);
							updateTask(t, COMPLETED, result);
						} catch (Exception ex) {
							LOG.log(Level.SEVERE, "Exception during task handling", ex);
							updateTask(t, FAILED);
						}
					}

				} else if (t.getState().equals(ABORT)) {
					State abort = abortTask(payload);
					updateTask(t, abort);
				}
			}
		} else if (inCls.isInstance(event.getData())) {
			T payload = (T) event.getData();
			State init = initializeTask(payload);
			if (init != REJECTED) {
				try {
					V result = handleTask(payload);
					send(result);
				} catch (Exception e) {
					send(e);
				}
			}
		}
	}

	public abstract State initializeTask(T payload);

	public abstract V handleTask(T payload) throws Exception;

	public State abortTask(T payload) {
		LOG.log(Level.WARNING, "Task abortion not supported, faking answer.");
		return ABORTED;
	}

	private void send(Object t) {
		try {
			this.informer.send(t);
		} catch (RSBException ex) {
			LOG.log(Level.SEVERE, "Exception during update", ex);
		}
	}

	private TaskState updateTask(TaskState t, State state, V result) {
		LOG.log(Level.INFO, "Update task with state ''{0}'' and payload ''{1}''", new Object[]{state, result});
		ByteString payload = outSerial.serialize(result);
		ByteString schema = outSerial.getSchema();
		TaskState update = TaskState.newBuilder().
				setOrigin(HANDLER).
				setState(state).
				setSerial(t.getSerial() + 1).
				setWireSchema(schema).
				setPayload(payload).
				build();
		send(update);
		return update;
	}

	private TaskState updateTask(TaskState t, State state) {
		LOG.log(Level.INFO, "Update task with state ''{0}''", state);
		TaskState update = TaskState.newBuilder().
				setOrigin(HANDLER).
				setState(state).
				setSerial(t.getSerial() + 1).
				setWireSchema(t.getWireSchema()).
				setPayload(t.getPayload()).
				build();
		send(update);
		return update;
	}

}
