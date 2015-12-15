/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.task.srv;

import com.google.protobuf.ByteString;
import de.citec.csra.task.SerializationService;
import java.nio.ByteBuffer;
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
import rsb.converter.WireContents;
import rst.communicationpatterns.TaskStateType.TaskState;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.HANDLER;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.SUBMITTER;
import rst.communicationpatterns.TaskStateType.TaskState.State;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORT;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORTED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.INITIATED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.REJECTED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public abstract class TaskHandler<T> implements Handler {

	static {
		DefaultConverterRepository.getDefaultConverterRepository().addConverter(new ProtocolBufferConverter<>(TaskState.getDefaultInstance()));
	}
	private final static Logger LOG = Logger.getLogger(TaskHandler.class.getName());
	private final Informer informer;
	private final Listener listener;
	private final Class<T> cls;
	private final SerializationService<T> serial;

	public TaskHandler(String scope, Class<T> cls) throws InitializeException {
		this.informer = Factory.getInstance().createInformer(scope);
		this.listener = Factory.getInstance().createListener(scope);
		this.cls = cls;
		this.serial = new SerializationService<>(cls);
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
				T payload = serial.deserialize(t.getPayload());
				if (t.getState().equals(INITIATED)) {

					State init = initializeTask(payload);
					updateTask(t, init);
					if (init != REJECTED) {
						State result = handleTask(payload);
						updateTask(t, result);
					}

				} else if (t.getState().equals(ABORT)) {
					State abort = abortTask(payload);
					updateTask(t, abort);
				}
			} else if (cls.isInstance(event.getData())) {
				T payload = (T) event.getData();
				State init = initializeTask(payload);
				if (init != REJECTED) {
					handleTask(payload);
				}
			}
		}
	}

	public abstract State initializeTask(T payload);

	public abstract State handleTask(T payload);

	public State abortTask(T payload) {
		LOG.log(Level.WARNING, "Task abortion not supported, faking answer.");
		return ABORTED;
	}

	private void sendTask(TaskState t) {
		try {
			this.informer.send(t);
		} catch (RSBException ex) {
			LOG.log(Level.SEVERE, "Exception during task update", ex);
		}
	}

	private void updateTask(TaskState t, State state, WireContents<ByteBuffer> serialized) {
		ByteString payload = ByteString.copyFrom(serialized.getSerialization());
		ByteString schema = ByteString.copyFromUtf8(serialized.getWireSchema());
		sendTask(TaskState.newBuilder().
				setOrigin(HANDLER).
				setState(state).
				setSerial(t.getSerial() + 1).
				setWireSchema(schema).
				setPayload(payload).
				build());
	}

	private void updateTask(TaskState t, State state) {
		sendTask(TaskState.newBuilder().
				setOrigin(HANDLER).
				setState(state).
				setSerial(t.getSerial() + 1).
				setWireSchema(t.getWireSchema()).
				setPayload(t.getPayload()).
				build());
	}

}
