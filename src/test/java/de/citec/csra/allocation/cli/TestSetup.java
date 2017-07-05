/*
 * Copyright (C) 2017 Patrick Holthaus
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
package de.citec.csra.allocation.cli;

import de.citec.csra.allocation.srv.AllocationServer;
import de.citec.csra.allocation.vis.MovingChart;
import static de.citec.csra.rst.util.IntervalUtils.currentTimeInMicros;
import static de.citec.csra.rst.util.StringRepresentation.shortString;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.fail;
import rsb.Factory;
import rsb.Listener;
import rsb.RSBException;
import rsb.config.ParticipantConfig;
import rsb.config.TransportConfig;

/**
 *
 * @author Patrick Holthaus
 */
public class TestSetup {

	private static final boolean useServer = true;
	private static final boolean useGUI = false;
	private static final Boolean useLOG = false;
	private static boolean initialized = false;
	private static boolean hasServer;
	private static boolean hasGUI;
	private static boolean hasLOG;

	public static void initServer() {
		synchronized (useLOG) {
			if (!initialized) {
				ParticipantConfig cfg = Factory.getInstance().getDefaultParticipantConfig();
				for (TransportConfig t : cfg.getTransports().values()) {
					t.setEnabled(t.getName().equalsIgnoreCase("INPROCESS"));
				}
				Factory.getInstance().setDefaultParticipantConfig(cfg);

				try {
					RemoteAllocationService.getInstance();
					RemoteAllocationService.getScope();
				} catch (RSBException ex) {
					Logger.getLogger(TestSetup.class.getName()).log(Level.SEVERE, "could not cache remote allocation service", ex);
				}
				initialized = true;
			}

			if (useLOG && !hasLOG) {
				new Thread(() -> {
					try {
						Listener li = Factory.getInstance().createListener(AllocationServer.getScope());
						li.addHandler((e) -> {
							System.out.println("RSB: " + currentTimeInMicros() + ": " + shortString(e.getData()));
						}, true);
						li.activate();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}).start();
				hasLOG = true;
			}

			if (useServer && !hasServer) {
				new Thread(() -> {
					try {
						AllocationServer a = AllocationServer.getInstance();
						a.activate();
						a.listen();
					} catch (InterruptedException | RSBException ex) {
						ex.printStackTrace();
						fail("Exception in server thread: " + ex);
					}
				}).start();
				hasServer = true;
			}

			if (useGUI && !hasGUI) {
				try {
					MovingChart.main(new String[]{"30000", "30000"});
					hasGUI = true;
				} catch (RSBException | InterruptedException ex) {
					Logger.getLogger(TestSetup.class.getName()).log(Level.SEVERE, "Exception during gui setup", ex);
				}
				hasGUI = true;
			}
		}
	}

	public static void shutdownServer() throws InterruptedException, RSBException {
		synchronized (useLOG) {
			if (!initialized) {
				ParticipantConfig cfg = Factory.getInstance().getDefaultParticipantConfig();
				for (TransportConfig t : cfg.getTransports().values()) {
					t.setEnabled(t.getName().equalsIgnoreCase("INPROCESS"));
				}
				Factory.getInstance().setDefaultParticipantConfig(cfg);

				try {
					RemoteAllocationService.getInstance();
					RemoteAllocationService.getScope();
				} catch (RSBException ex) {
					Logger.getLogger(TestSetup.class.getName()).log(Level.SEVERE, "could not cache remote allocation service", ex);
				}
				initialized = true;
			}
			if (hasServer) {
				AllocationServer.getInstance().deactivate();
				hasServer = false;
			}
		}
	}
}
