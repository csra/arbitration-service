/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.allocation;

import de.citec.csra.allocation.srv.AllocationServer;
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
import rst.communicationpatterns.ResourceAllocationType;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class RemoteAllocationService {

	static {
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(ResourceAllocationType.ResourceAllocation.getDefaultInstance()));
	}

	private static RemoteAllocationService instance;
	private final static Logger LOG = Logger.getLogger(RemoteAllocationService.class.getName());

	private final Informer informer;
	private final Listener listener;

	public static RemoteAllocationService getInstance() throws InitializeException, RSBException {
		if (instance == null) {
			instance = new RemoteAllocationService();
		}
		return instance;
	}

	private RemoteAllocationService() throws InitializeException, RSBException {
		this.informer = Factory.getInstance().createInformer(AllocationServer.getScope());
		this.listener = Factory.getInstance().createListener(AllocationServer.getScope());
		this.listener.addFilter(new OriginFilter(this.informer.getId(), true));
		this.listener.activate();
		this.informer.activate();
	}

	public void update(ResourceAllocation allocation) throws RSBException {
		synchronized (this.informer) {
			this.informer.publish(allocation);
		}
	}

	public void addHandler(Handler handler, boolean wait) throws InterruptedException, RSBException {
		this.listener.addHandler(handler, wait);
	}

	public void removeHandler(Handler handler, boolean wait) throws InterruptedException, RSBException {
		this.listener.removeHandler(handler, wait);
	}

	public void shutdown() throws RSBException, InterruptedException {
		this.informer.deactivate();
		this.listener.deactivate();
		instance = null;
	}

}
