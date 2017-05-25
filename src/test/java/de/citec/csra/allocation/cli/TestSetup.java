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
import de.citec.csra.rst.util.StringRepresentation;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.AfterClass;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import rsb.Factory;
import rsb.RSBException;
import rsb.config.ParticipantConfig;
import rsb.config.TransportConfig;

/**
 *
 * @author Patrick Holthaus
 */
public class TestSetup {

	private static final boolean useGUI = false;
	private static boolean hasGUI;

	@BeforeClass
	public static void initServer() throws InterruptedException {

		ParticipantConfig cfg = Factory.getInstance().getDefaultParticipantConfig();
		for (TransportConfig t : cfg.getTransports().values()) {
			t.setEnabled(t.getName().equalsIgnoreCase("INPROCESS"));
		}
		Factory.getInstance().setDefaultParticipantConfig(cfg);
		new Thread(() -> {
			try {
				AllocationServer a = AllocationServer.getInstance();
				a.activate();
				a.listen();
			} catch (InterruptedException | RSBException ex) {
				fail("Exception in server thread: " + ex);
			}
		}).start();
		Thread.sleep(100);

		if (useGUI && !hasGUI) {
			try {
				MovingChart.main(new String[]{"100", "20000"});
				hasGUI = true;
			} catch (RSBException ex) {
				Logger.getLogger(TestSetup.class.getName()).log(Level.SEVERE, null, ex);
			}
			Thread.sleep(100);
		}
		
		StringRepresentation.setIntervalOrigin(System.currentTimeMillis());

	}

	@AfterClass
	public static void shutdownServer() throws InterruptedException, RSBException {
		AllocationServer.getInstance().deactivate();
		Thread.sleep(100);
	}
}
