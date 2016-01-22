/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.task.srv;

import com.google.protobuf.ByteString;
import de.citec.csra.task.SerializationService;
import de.citec.csra.util.StringParser;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Factory;
import rsb.Handler;
import rsb.Informer;
import rsb.InitializeException;
import rsb.Listener;
import rsb.RSBException;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rsb.filter.OriginFilter;
import rst.communicationpatterns.TaskStateType;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.HANDLER;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORTED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public abstract class AbstractTaskHandler<T, V> implements RSBTaskHandler, Handler {

	static {
		DefaultConverterRepository.getDefaultConverterRepository().addConverter(new ProtocolBufferConverter<>(TaskStateType.TaskState.getDefaultInstance()));
	}
	private final static Logger LOG = Logger.getLogger(AsyncTaskHandler.class.getName());
	private final Informer informer;
	private final Listener listener;
	private final Class<T> inCls;
	private final SerializationService<T> inSerial;
	private final SerializationService<V> outSerial;
	private StringParser<T> parser;

	public AbstractTaskHandler(String scope, Class<T> cls, Class<V> res) throws InitializeException {
		this.informer = Factory.getInstance().createInformer(scope);
		this.listener = Factory.getInstance().createListener(scope);
		this.listener.addFilter(new OriginFilter(this.informer.getId(), true));
		this.inCls = cls;
		this.inSerial = new SerializationService<>(cls);
		this.outSerial = new SerializationService<>(res);
	}

	public AbstractTaskHandler(String scope, Class<T> cls, Class<V> res, StringParser<T> p) throws InitializeException {
		this(scope, cls, res);
		this.parser = p;
	}

	@Override
	public void activate() throws RSBException, InterruptedException {
		this.listener.addHandler(this, true);
		this.informer.activate();
		this.listener.activate();
		LOG.log(Level.FINE, "Activated listener/informer pair at ''{0}''", listener.getScope());
	}

	@Override
	public void deactivate() throws InterruptedException, RSBException {
		this.listener.deactivate();
		this.informer.deactivate();
	}

	public T deserialize(ByteString b) {
		return inSerial.deserialize(b);
	}

	public TaskStateType.TaskState.State abortTask(T payload) {
		LOG.log(Level.WARNING, "Task abortion not supported, faking answer.");
		return ABORTED;
	}

	public Class<T> getInClass() {
		return this.inCls;
	}
	
	public StringParser<T> getParser(){
		return this.parser;
	}

	public void respond(Object t) {
		LOG.log(Level.INFO, "Responding with ''{0}''", t);
		send(t);
	}

	private void send(Object t) {
		try {
			if (t != null) {
				this.informer.send(t);
			}
		} catch (RSBException ex) {
			LOG.log(Level.SEVERE, "Exception while sending message via rsb", ex);
		}
	}

	public void updateTask(TaskStateType.TaskState t, TaskStateType.TaskState.State state, V result) {
		LOG.log(Level.INFO, "Update task with state ''{0}'' and payload ''{1}''", new Object[]{state, result});
		ByteString payload = outSerial.serialize(result);
		ByteString schema = outSerial.getSchema();
		TaskStateType.TaskState update = TaskStateType.TaskState.newBuilder().
				setOrigin(HANDLER).
				setState(state).
				setSerial(t.getSerial() + 1).
				setWireSchema(schema).
				setPayload(payload).
				build();
		send(update);
	}

	public void updateTask(TaskStateType.TaskState t, TaskStateType.TaskState.State state) {
		LOG.log(Level.INFO, "Update task with state ''{0}''", state);
		TaskStateType.TaskState update = TaskStateType.TaskState.newBuilder().
				setOrigin(HANDLER).
				setState(state).
				setSerial(t.getSerial() + 1).
				setWireSchema(t.getWireSchema()).
				setPayload(t.getPayload()).
				build();
		send(update);
	}
}
