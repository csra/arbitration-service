/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.arbitration.srv;

import rsb.InitializeException;
import rsb.RSBException;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class Arbitration {

	public static void main(String[] args) throws InitializeException, RSBException, InterruptedException {
		ArbitrationServer ts = new ArbitrationServer();
		ts.activate();
		ts.waitForShutdown();
	}

}
